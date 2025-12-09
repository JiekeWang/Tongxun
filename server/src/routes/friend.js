const express = require('express');
const { body, query, validationResult } = require('express-validator');
const { v4: uuidv4 } = require('uuid');
const { getPool } = require('../config/database');
const { authenticateToken } = require('../middleware/auth');
const { sendToUser } = require('../websocket/socketHandler');
const logger = require('../utils/logger');

const router = express.Router();

// 所有路由需要认证
router.use(authenticateToken);

// 发送好友请求
router.post('/request', [
  body('toUserId').isUUID().withMessage('用户ID格式不正确'),
  body('message').optional().isLength({ max: 200 })
], async (req, res) => {
  try {
    const pool = getPool();
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    const { toUserId, message } = req.body;
    const fromUserId = req.user.userId;

    if (fromUserId === toUserId) {
      return res.status(400).json({ error: '不能添加自己为好友' });
    }

    // 检查目标用户是否存在
    const [users] = await pool.query(
      'SELECT user_id FROM users WHERE user_id = ?',
      [toUserId]
    );

    if (users.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }

    // 检查是否已经是好友
    const [existingFriends] = await pool.query(
      'SELECT id FROM friends WHERE user_id = ? AND friend_id = ?',
      [fromUserId, toUserId]
    );

    if (existingFriends.length > 0) {
      return res.status(400).json({ error: '已经是好友关系' });
    }

    // 检查是否已有待处理的好友请求
    const [existingRequests] = await pool.query(
      'SELECT request_id FROM friend_requests WHERE from_user_id = ? AND to_user_id = ? AND status = ?',
      [fromUserId, toUserId, 'PENDING']
    );

    if (existingRequests.length > 0) {
      return res.status(400).json({ error: '已发送好友请求，等待对方处理' });
    }

    // 创建好友请求
    const requestId = uuidv4();
    await pool.query(
      'INSERT INTO friend_requests (request_id, from_user_id, to_user_id, message) VALUES (?, ?, ?, ?)',
      [requestId, fromUserId, toUserId, message || null]
    );

    // 获取发送者信息，用于通知
    const [fromUsers] = await pool.query(
      'SELECT user_id, nickname, avatar FROM users WHERE user_id = ?',
      [fromUserId]
    );
    const fromUser = fromUsers[0];

    // 通过WebSocket推送好友请求通知给被添加人
    try {
      const notificationData = {
        requestId,
        fromUserId,
        fromUserNickname: fromUser?.nickname || '',
        fromUserAvatar: fromUser?.avatar || null,
        message: message || null,
        timestamp: Date.now()
      };
      
      logger.info(`准备发送好友请求通知 - toUserId: ${toUserId}, requestId: ${requestId}, data: ${JSON.stringify(notificationData)}`);
      
      await sendToUser(toUserId, 'friend_request', notificationData);
      
      logger.info(`好友请求通知已推送 - toUserId: ${toUserId}, requestId: ${requestId}`);
    } catch (wsError) {
      // WebSocket推送失败不影响好友请求的创建
      logger.error(`好友请求通知推送失败，但请求已创建 - toUserId: ${toUserId}, requestId: ${requestId}`, wsError);
    }

    logger.info(`好友请求已创建 - requestId: ${requestId}, fromUserId: ${fromUserId}, toUserId: ${toUserId}`);

    res.status(201).json({
      requestId,
      message: '好友请求已发送'
    });
  } catch (error) {
    logger.error('发送好友请求失败:', error);
    res.status(500).json({ error: '发送好友请求失败' });
  }
});

// 接受好友请求
router.post('/accept', [
  query('requestId').isUUID().withMessage('请求ID格式不正确')
], async (req, res) => {
  try {
    const pool = getPool();
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    const { requestId } = req.query;
    const userId = req.user.userId;

    // 查找好友请求
    const [requests] = await pool.query(
      'SELECT * FROM friend_requests WHERE request_id = ? AND to_user_id = ? AND status = ?',
      [requestId, userId, 'PENDING']
    );

    if (requests.length === 0) {
      return res.status(404).json({ error: '好友请求不存在或已处理' });
    }

    const friendRequest = requests[0];

    // 更新请求状态
    await pool.query(
      'UPDATE friend_requests SET status = ? WHERE request_id = ?',
      ['ACCEPTED', requestId]
    );

    // 建立好友关系（双向）
    await pool.query(
      'INSERT INTO friends (user_id, friend_id) VALUES (?, ?)',
      [userId, friendRequest.from_user_id]
    );

    await pool.query(
      'INSERT INTO friends (user_id, friend_id) VALUES (?, ?)',
      [friendRequest.from_user_id, userId]
    );

    res.json({ message: '好友请求已接受' });
  } catch (error) {
    logger.error('接受好友请求失败:', error);
    res.status(500).json({ error: '接受好友请求失败' });
  }
});

// 拒绝好友请求
router.post('/reject', [
  query('requestId').isUUID().withMessage('请求ID格式不正确')
], async (req, res) => {
  try {
    const pool = getPool();
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    const { requestId } = req.query;
    const userId = req.user.userId;

    // 更新请求状态
    const [result] = await pool.query(
      'UPDATE friend_requests SET status = ? WHERE request_id = ? AND to_user_id = ? AND status = ?',
      ['REJECTED', requestId, userId, 'PENDING']
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ error: '好友请求不存在或已处理' });
    }

    res.json({ message: '好友请求已拒绝' });
  } catch (error) {
    logger.error('拒绝好友请求失败:', error);
    res.status(500).json({ error: '拒绝好友请求失败' });
  }
});

// 获取好友列表
router.get('/', async (req, res) => {
  try {
    const pool = getPool();
    const userId = req.user.userId;

    const [friends] = await pool.query(
      `SELECT f.friend_id, f.remark, f.group_name, f.is_blocked,
              u.nickname, u.avatar, u.signature
       FROM friends f
       JOIN users u ON f.friend_id = u.user_id
       WHERE f.user_id = ? AND f.is_blocked = FALSE
       ORDER BY f.created_at DESC`,
      [userId]
    );

    res.json(friends.map(friend => ({
      friendId: friend.friend_id,
      remark: friend.remark,
      groupName: friend.group_name,
      nickname: friend.nickname,
      avatar: friend.avatar,
      signature: friend.signature
    })));
  } catch (error) {
    logger.error('获取好友列表失败:', error);
    res.status(500).json({ error: '获取好友列表失败' });
  }
});

// 获取好友请求列表
router.get('/requests', async (req, res) => {
  try {
    const pool = getPool();
    const userId = req.user.userId;

    // 收到的请求
    const [receivedRequests] = await pool.query(
      `SELECT fr.request_id, fr.from_user_id, fr.message, fr.created_at,
              u.nickname, u.avatar
       FROM friend_requests fr
       JOIN users u ON fr.from_user_id = u.user_id
       WHERE fr.to_user_id = ? AND fr.status = ?
       ORDER BY fr.created_at DESC`,
      [userId, 'PENDING']
    );

    // 发送的请求
    const [sentRequests] = await pool.query(
      `SELECT fr.request_id, fr.to_user_id, fr.message, fr.status, fr.created_at,
              u.nickname, u.avatar
       FROM friend_requests fr
       JOIN users u ON fr.to_user_id = u.user_id
       WHERE fr.from_user_id = ? AND fr.status = ?
       ORDER BY fr.created_at DESC`,
      [userId, 'PENDING']
    );

    res.json({
      received: receivedRequests.map(req => ({
        requestId: req.request_id,
        fromUserId: req.from_user_id,
        nickname: req.nickname,
        avatar: req.avatar,
        message: req.message,
        createdAt: req.created_at ? new Date(req.created_at).getTime() : Date.now()
      })),
      sent: sentRequests.map(req => ({
        requestId: req.request_id,
        toUserId: req.to_user_id,
        nickname: req.nickname,
        avatar: req.avatar,
        message: req.message,
        status: req.status,
        createdAt: req.created_at ? new Date(req.created_at).getTime() : Date.now()
      }))
    });
  } catch (error) {
    logger.error('获取好友请求列表失败:', error);
    res.status(500).json({ error: '获取好友请求列表失败' });
  }
});

// 删除好友
router.delete('/:friendId', async (req, res) => {
  try {
    const pool = getPool();
    const { friendId } = req.params;
    const userId = req.user.userId;

    // 删除双向好友关系
    await pool.query(
      'DELETE FROM friends WHERE user_id = ? AND friend_id = ?',
      [userId, friendId]
    );

    await pool.query(
      'DELETE FROM friends WHERE user_id = ? AND friend_id = ?',
      [friendId, userId]
    );

    res.json({ message: '好友已删除' });
  } catch (error) {
    logger.error('删除好友失败:', error);
    res.status(500).json({ error: '删除好友失败' });
  }
});

// 拉黑/取消拉黑
router.post('/block', [
  body('friendId').isUUID().withMessage('用户ID格式不正确'),
  body('isBlocked').isBoolean().withMessage('isBlocked必须是布尔值')
], async (req, res) => {
  try {
    const pool = getPool();
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    const { friendId, isBlocked } = req.body;
    const userId = req.user.userId;

    await pool.query(
      'UPDATE friends SET is_blocked = ? WHERE user_id = ? AND friend_id = ?',
      [isBlocked, userId, friendId]
    );

    res.json({ message: isBlocked ? '已拉黑' : '已取消拉黑' });
  } catch (error) {
    logger.error('拉黑操作失败:', error);
    res.status(500).json({ error: '操作失败' });
  }
});

module.exports = router;

