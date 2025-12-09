const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const { body, validationResult } = require('express-validator');
const { getPool } = require('../config/database');
const { setSession, deleteSession } = require('../config/redis');
const { disconnectUserSockets } = require('../websocket/socketHandler');
const logger = require('../utils/logger');

const router = express.Router();

// 注册
router.post('/register', [
  body('phoneNumber')
    .trim()
    .isMobilePhone('zh-CN')
    .withMessage('手机号格式不正确'),
  body('password')
    .trim()
    .isLength({ min: 6, max: 50 })
    .withMessage('密码长度必须在6-50位之间')
    .matches(/^[a-zA-Z0-9!@#$%^&*()_+\-=\[\]{}|;:,.<>?]+$/)
    .withMessage('密码只能包含字母、数字和常用符号'),
  body('nickname')
    .trim()
    .isLength({ min: 1, max: 50 })
    .withMessage('昵称长度必须在1-50个字符之间')
    .matches(/^[\u4e00-\u9fa5a-zA-Z0-9_\-\s]+$/)
    .withMessage('昵称只能包含中文、英文、数字、下划线和连字符')
], async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    const pool = getPool();
    // 清理输入：去除首尾空格
    const phoneNumber = (req.body.phoneNumber || '').trim();
    const password = (req.body.password || '').trim();
    const nickname = (req.body.nickname || '').trim();

    // 防御性检查：再次验证输入
    if (!phoneNumber || !password || !nickname) {
      return res.status(400).json({ error: '所有字段不能为空' });
    }

    if (phoneNumber.length !== 11) {
      return res.status(400).json({ error: '手机号必须是11位数字' });
    }

    // 检查手机号是否已注册
    const [existingUsers] = await pool.query(
      'SELECT user_id FROM users WHERE phone_number = ?',
      [phoneNumber]
    );

    if (existingUsers.length > 0) {
      return res.status(400).json({ error: '该手机号已注册' });
    }

    // 加密密码
    const passwordHash = await bcrypt.hash(password, 10);
    const userId = uuidv4();

    // 创建用户（使用参数化查询防止SQL注入）
    const [result] = await pool.query(
      'INSERT INTO users (user_id, phone_number, nickname, password_hash) VALUES (?, ?, ?, ?)',
      [userId, phoneNumber, nickname, passwordHash]
    );

    // 验证插入是否成功
    if (!result || result.affectedRows !== 1) {
      logger.error('用户插入失败:', { userId, phoneNumber });
      return res.status(500).json({ error: '注册失败，请稍后重试' });
    }

    // 生成Token
    const token = jwt.sign(
      { userId, phoneNumber, nickname },
      process.env.JWT_SECRET || 'default-secret-key',
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    // 保存Session（如果失败不影响注册流程）
    try {
      await setSession(userId, token);
    } catch (sessionError) {
      logger.warn('Session保存失败，但用户已注册:', sessionError);
      // 继续执行，不中断注册流程
    }

    // 返回用户信息（不包含密码）
    const [users] = await pool.query(
      'SELECT user_id, phone_number, nickname, avatar, signature FROM users WHERE user_id = ?',
      [userId]
    );

    if (!users || users.length === 0) {
      logger.error('用户注册成功但查询失败:', { userId });
      return res.status(500).json({ error: '注册成功，但获取用户信息失败' });
    }

    res.status(201).json({
      token,
      user: {
        userId: users[0].user_id,
        phoneNumber: users[0].phone_number,
        nickname: users[0].nickname,
        avatar: users[0].avatar || null,
        signature: users[0].signature || null
      },
      expiresIn: 7 * 24 * 60 * 60 // 7天
    });
  } catch (error) {
    logger.error('注册失败:', error);
    
    // 根据错误类型返回不同的错误信息
    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(400).json({ error: '该手机号已注册' });
    }
    
    if (error.code === 'ECONNREFUSED' || error.code === 'ETIMEDOUT') {
      return res.status(503).json({ error: '服务暂时不可用，请稍后重试' });
    }
    
    res.status(500).json({ error: '注册失败，请稍后重试' });
  }
});

// 登录
router.post('/login', [
  body('phoneNumber')
    .trim()
    .isMobilePhone('zh-CN')
    .withMessage('手机号格式不正确'),
  body('password')
    .trim()
    .notEmpty()
    .withMessage('密码不能为空')
    .isLength({ min: 6, max: 50 })
    .withMessage('密码长度必须在6-50位之间')
], async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    const pool = getPool();
    // 清理输入：去除首尾空格
    const phoneNumber = (req.body.phoneNumber || '').trim();
    const password = (req.body.password || '').trim();

    // 防御性检查：再次验证输入
    if (!phoneNumber || !password) {
      return res.status(400).json({ error: '手机号和密码不能为空' });
    }

    // 查找用户（使用参数化查询防止SQL注入）
    const [users] = await pool.query(
      'SELECT user_id, phone_number, nickname, password_hash, avatar, signature FROM users WHERE phone_number = ?',
      [phoneNumber]
    );

    // 统一错误信息，防止用户枚举攻击
    if (users.length === 0) {
      // 使用 bcrypt 进行固定时间比较，防止时序攻击
      await bcrypt.compare(password, '$2a$10$dummyHashToPreventTimingAttack');
      return res.status(401).json({ error: '手机号或密码错误' });
    }

    const user = users[0];

    // 验证密码（使用 bcrypt 防止时序攻击）
    const isValidPassword = await bcrypt.compare(password, user.password_hash);
    if (!isValidPassword) {
      return res.status(401).json({ error: '手机号或密码错误' });
    }

    // 检查用户是否已在其他设备登录，如果是则断开旧设备连接
    try {
      await disconnectUserSockets(user.user_id, '账号在其他设备登录');
      logger.info(`已断开用户旧设备连接 - userId: ${user.user_id}`);
    } catch (disconnectError) {
      logger.warn('断开旧设备连接失败，但继续登录流程:', disconnectError);
      // 继续执行，不中断登录流程
    }

    // 生成Token
    const token = jwt.sign(
      { userId: user.user_id, phoneNumber: user.phone_number, nickname: user.nickname },
      process.env.JWT_SECRET || 'default-secret-key',
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    // 保存Session（如果失败不影响登录流程）
    try {
      await setSession(user.user_id, token);
    } catch (sessionError) {
      logger.warn('Session保存失败，但用户已登录:', sessionError);
      // 继续执行，不中断登录流程
    }

    // 返回用户信息（不包含密码）
    res.json({
      token,
      user: {
        userId: user.user_id,
        phoneNumber: user.phone_number,
        nickname: user.nickname,
        avatar: user.avatar || null,
        signature: user.signature || null
      },
      expiresIn: 7 * 24 * 60 * 60 // 7天
    });
  } catch (error) {
    logger.error('登录失败:', error);
    
    // 根据错误类型返回不同的错误信息
    if (error.code === 'ECONNREFUSED' || error.code === 'ETIMEDOUT') {
      return res.status(503).json({ error: '服务暂时不可用，请稍后重试' });
    }
    
    res.status(500).json({ error: '登录失败，请稍后重试' });
  }
});

// 登出
router.post('/logout', async (req, res) => {
  try {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (token) {
      try {
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        await deleteSession(decoded.userId);
      } catch (error) {
        // Token可能已过期，忽略错误
      }
    }

    res.json({ message: '登出成功' });
  } catch (error) {
    logger.error('登出失败:', error);
    res.status(500).json({ error: '登出失败' });
  }
});

// 刷新Token（可选）
router.post('/refresh', async (req, res) => {
  try {
    const { refreshToken } = req.body;
    
    if (!refreshToken) {
      return res.status(400).json({ error: '未提供刷新令牌' });
    }

    const decoded = jwt.verify(refreshToken, process.env.JWT_SECRET);
    
    // 生成新Token
    const newToken = jwt.sign(
      { userId: decoded.userId, phoneNumber: decoded.phoneNumber, nickname: decoded.nickname },
      process.env.JWT_SECRET,
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    // 更新Session
    await setSession(decoded.userId, newToken);

    res.json({
      token: newToken,
      expiresIn: 7 * 24 * 60 * 60
    });
  } catch (error) {
    logger.error('刷新Token失败:', error);
    res.status(401).json({ error: '刷新令牌无效或已过期' });
  }
});

module.exports = router;

