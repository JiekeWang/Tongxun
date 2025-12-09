const express = require('express');
const { query, validationResult } = require('express-validator');
const { getPool } = require('../config/database');
const { authenticateToken } = require('../middleware/auth');
const logger = require('../utils/logger');

const router = express.Router();

// 所有路由需要认证
router.use(authenticateToken);

// 搜索用户（支持手机号和用户ID搜索）- 必须在 /:userId 之前定义，避免路由冲突
router.get('/search', [
  query('phone').optional().custom((value) => {
    if (!value) return true;
    // 验证手机号格式：11位数字，以1开头，第二位是3-9
    const trimmed = String(value).trim();
    const phoneRegex = /^1[3-9]\d{9}$/;
    if (!phoneRegex.test(trimmed)) {
      throw new Error('手机号格式不正确');
    }
    return true;
  }),
  query('userId').optional()
], async (req, res) => {
  const requestId = Date.now();
  try {
    // 记录完整请求信息
    logger.info(`[搜索${requestId}] 请求开始`, {
      method: req.method,
      url: req.url,
      fullUrl: req.protocol + '://' + req.get('host') + req.originalUrl,
      query: req.query,
      queryString: JSON.stringify(req.query),
      headers: {
        authorization: req.headers.authorization ? 'Bearer ***' : 'none',
        'user-agent': req.headers['user-agent']
      }
    });

    const pool = getPool();
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      logger.warn(`[搜索${requestId}] 验证失败`, { 
        errors: errors.array(),
        rawQuery: req.query
      });
      return res.status(400).json({ errors: errors.array() });
    }

    // 清理输入
    const phone = req.query.phone ? String(req.query.phone).trim() : null;
    const userId = req.query.userId ? String(req.query.userId).trim() : null;

    logger.info(`[搜索${requestId}] 参数解析`, {
      rawPhone: req.query.phone,
      rawUserId: req.query.userId,
      phone: phone || null,
      userId: userId || null,
      phoneType: typeof phone,
      userIdType: typeof userId,
      phoneLength: phone ? phone.length : 0,
      userIdLength: userId ? userId.length : 0,
      phoneIsEmpty: phone === '',
      userIdIsEmpty: userId === ''
    });

    // 验证参数：必须提供且只能提供一个
    if (!phone && !userId) {
      logger.warn(`[搜索${requestId}] 未提供参数`, {
        phone: phone,
        userId: userId,
        query: req.query
      });
      return res.status(400).json({ error: '请提供手机号或用户ID' });
    }

    if (phone && userId) {
      logger.warn(`[搜索${requestId}] 参数冲突`, {
        phone,
        userId
      });
      return res.status(400).json({ error: '不能同时提供手机号和用户ID' });
    }

    let querySql = 'SELECT user_id, phone_number, nickname, avatar, signature FROM users WHERE ';
    let queryParams = [];

    if (phone) {
      // 使用精确匹配搜索手机号
      querySql += 'phone_number = ?';
      queryParams.push(phone);
      logger.info(`[搜索${requestId}] 使用手机号查询`, {
        phone,
        phoneType: typeof phone,
        phoneLength: phone.length,
        phoneRegexMatch: /^1[3-9]\d{9}$/.test(phone),
        phoneCharCodes: phone.split('').map(c => c.charCodeAt(0))
      });
    } else if (userId) {
      // 支持UUID格式或普通字符串ID
      querySql += 'user_id = ?';
      queryParams.push(userId);
      logger.info(`[搜索${requestId}] 使用用户ID查询`, {
        userId,
        userIdType: typeof userId,
        userIdLength: userId.length
      });
    }

    logger.info(`[搜索${requestId}] 执行SQL`, {
      sql: querySql,
      params: queryParams,
      paramsCount: queryParams.length,
      paramsTypes: queryParams.map(p => typeof p)
    });

    const startTime = Date.now();
    const [users] = await pool.query(querySql, queryParams);
    const queryTime = Date.now() - startTime;
    
    logger.info(`[搜索${requestId}] SQL查询完成`, {
      resultCount: users.length,
      queryTime: `${queryTime}ms`,
      results: users.length > 0 ? users.map(u => ({
        userId: u.user_id,
        phoneNumber: u.phone_number,
        nickname: u.nickname,
        hasAvatar: !!u.avatar,
        hasSignature: !!u.signature
      })) : []
    });

    if (users.length === 0) {
      logger.info(`[搜索${requestId}] 未找到用户`, {
        searchType: phone ? 'phone' : 'userId',
        searchValue: phone || userId,
        sql: querySql,
        params: queryParams
      });
      return res.status(404).json({ error: '未找到该用户' });
    }

    // 返回用户信息（不包含敏感信息）
    const userData = {
      userId: users[0].user_id,
      phoneNumber: users[0].phone_number,
      nickname: users[0].nickname,
      avatar: users[0].avatar || null,
      signature: users[0].signature || null
    };
    
    logger.info(`[搜索${requestId}] 搜索成功`, {
      userId: userData.userId,
      nickname: userData.nickname,
      phoneNumber: userData.phoneNumber,
      hasAvatar: !!userData.avatar,
      hasSignature: !!userData.signature,
      responseData: JSON.stringify(userData)
    });

    res.json(userData);
  } catch (error) {
    logger.error(`[搜索${requestId}] 搜索失败`, {
      error: error.message,
      stack: error.stack,
      name: error.name,
      code: error.code,
      query: req.query
    });
    res.status(500).json({ error: '搜索用户失败，请稍后重试' });
  }
});

// 获取用户信息 - 必须在 /search 之后定义
router.get('/:userId', async (req, res) => {
  const requestId = Date.now();
  try {
    const pool = getPool();
    const { userId } = req.params;

    logger.info(`[获取用户${requestId}] 请求开始 - userId: ${userId}`);

    const [users] = await pool.query(
      'SELECT user_id, phone_number, nickname, avatar, signature FROM users WHERE user_id = ?',
      [userId]
    );

    if (users.length === 0) {
      logger.warn(`[获取用户${requestId}] 用户不存在 - userId: ${userId}`);
      return res.status(404).json({ error: '用户不存在' });
    }

    logger.info(`[获取用户${requestId}] 查询结果`, {
      userId: users[0].user_id,
      nickname: users[0].nickname,
      avatar: users[0].avatar ? `${users[0].avatar.substring(0, 50)}...` : null,
      avatarLength: users[0].avatar ? users[0].avatar.length : 0,
      hasAvatar: !!users[0].avatar,
      fullAvatar: users[0].avatar // 完整头像URL用于调试
    });
    
    // 转换为驼峰格式返回
    const userData = {
      userId: users[0].user_id,
      phoneNumber: users[0].phone_number,
      nickname: users[0].nickname || '',
      avatar: users[0].avatar || null,
      signature: users[0].signature || null
    };
    
    logger.info(`[获取用户${requestId}] 返回数据`, {
      userId: userData.userId,
      nickname: userData.nickname,
      avatar: userData.avatar ? `${userData.avatar.substring(0, 50)}...` : null,
      avatarLength: userData.avatar ? userData.avatar.length : 0,
      hasAvatar: !!userData.avatar,
      fullAvatar: userData.avatar // 完整头像URL用于调试
    });
    
    res.json(userData);
  } catch (error) {
    logger.error(`[获取用户${requestId}] 获取用户信息失败:`, {
      error: error.message,
      stack: error.stack,
      userId: req.params.userId
    });
    res.status(500).json({ error: '获取用户信息失败' });
  }
});

// 更新用户信息
router.put('/:userId', async (req, res) => {
  const requestId = Date.now();
  try {
    const pool = getPool();
    const { userId } = req.params;
    const { nickname, avatar, signature } = req.body;

    logger.info(`[更新用户${requestId}] 请求开始`, {
      userId: userId,
      requestUserId: req.user.userId,
      body: {
        nickname: nickname ? `${nickname.substring(0, 10)}...` : undefined,
        avatar: avatar ? `${avatar.substring(0, 50)}...` : undefined,
        signature: signature ? `${signature.substring(0, 10)}...` : undefined
      },
      avatarLength: avatar ? avatar.length : 0,
      hasAvatar: !!avatar
    });

    // 验证权限（只能修改自己的信息）
    if (req.user.userId !== userId) {
      logger.warn(`[更新用户${requestId}] 权限不足`, {
        userId: userId,
        requestUserId: req.user.userId
      });
      return res.status(403).json({ error: '无权修改他人信息' });
    }

    const updates = [];
    const values = [];

    if (nickname !== undefined) {
      updates.push('nickname = ?');
      values.push(nickname);
      logger.info(`[更新用户${requestId}] 准备更新昵称: ${nickname}`);
    }
    if (avatar !== undefined) {
      updates.push('avatar = ?');
      values.push(avatar);
      logger.info(`[更新用户${requestId}] 准备更新头像`, {
        avatar: avatar,
        avatarLength: avatar ? avatar.length : 0,
        avatarPrefix: avatar ? avatar.substring(0, 50) : null,
        avatarType: typeof avatar,
        avatarIsNull: avatar === null,
        avatarIsUndefined: avatar === undefined
      });
    } else {
      logger.warn(`[更新用户${requestId}] avatar字段未提供 (undefined)`);
    }
    if (signature !== undefined) {
      updates.push('signature = ?');
      values.push(signature);
      logger.info(`[更新用户${requestId}] 准备更新签名: ${signature}`);
    }

    if (updates.length === 0) {
      logger.warn(`[更新用户${requestId}] 没有要更新的字段`);
      return res.status(400).json({ error: '没有要更新的字段' });
    }

    values.push(userId);

    const updateSql = `UPDATE users SET ${updates.join(', ')} WHERE user_id = ?`;
    logger.info(`[更新用户${requestId}] 执行SQL更新`, {
      sql: updateSql,
      values: values.map((v, i) => i === values.length - 1 ? v : (typeof v === 'string' && v.length > 50 ? `${v.substring(0, 50)}...` : v))
    });

    const [updateResult] = await pool.query(updateSql, values);
    logger.info(`[更新用户${requestId}] SQL更新完成`, {
      affectedRows: updateResult.affectedRows,
      changedRows: updateResult.changedRows
    });

    // 返回更新后的用户信息
    logger.info(`[更新用户${requestId}] 查询更新后的用户信息`);
    const [users] = await pool.query(
      'SELECT user_id, phone_number, nickname, avatar, signature FROM users WHERE user_id = ?',
      [userId]
    );

    if (users.length === 0) {
      logger.error(`[更新用户${requestId}] 用户不存在 - userId: ${userId}`);
      return res.status(404).json({ error: '用户不存在' });
    }

    logger.info(`[更新用户${requestId}] 查询结果`, {
      userId: users[0].user_id,
      nickname: users[0].nickname,
      avatar: users[0].avatar ? `${users[0].avatar.substring(0, 50)}...` : null,
      avatarLength: users[0].avatar ? users[0].avatar.length : 0,
      hasAvatar: !!users[0].avatar
    });

    // 转换为驼峰格式返回
    const userData = {
      userId: users[0].user_id,
      phoneNumber: users[0].phone_number,
      nickname: users[0].nickname || '',
      avatar: users[0].avatar || null,
      signature: users[0].signature || null
    };

    logger.info(`[更新用户${requestId}] 返回数据`, {
      userId: userData.userId,
      nickname: userData.nickname,
      avatar: userData.avatar ? `${userData.avatar.substring(0, 50)}...` : null,
      avatarLength: userData.avatar ? userData.avatar.length : 0,
      hasAvatar: !!userData.avatar,
      fullAvatar: userData.avatar // 完整头像URL用于调试
    });

    res.json(userData);
  } catch (error) {
    logger.error(`[更新用户${requestId}] 更新用户信息失败:`, {
      error: error.message,
      stack: error.stack,
      userId: req.params.userId
    });
    res.status(500).json({ error: '更新用户信息失败' });
  }
});

module.exports = router;

