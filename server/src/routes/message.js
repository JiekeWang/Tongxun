const express = require('express');
const { body, query, validationResult } = require('express-validator');
const { v4: uuidv4 } = require('uuid');
const { getPool } = require('../config/database');
const { authenticateToken } = require('../middleware/auth');
const logger = require('../utils/logger');

const router = express.Router();

// 所有路由需要认证
router.use(authenticateToken);

// 撤回消息
router.post('/:messageId/recall', async (req, res) => {
  try {
    const pool = getPool();
    const { messageId } = req.params;
    const userId = req.user.userId;

    // 查找消息
    const [messages] = await pool.query(
      'SELECT * FROM messages WHERE message_id = ?',
      [messageId]
    );

    if (messages.length === 0) {
      return res.status(404).json({ error: '消息不存在' });
    }

    const message = messages[0];

    // 验证权限（只能撤回自己的消息）
    if (message.sender_id !== userId) {
      return res.status(403).json({ error: '只能撤回自己的消息' });
    }

    // 检查撤回时间限制（2分钟内）
    const recallWindow = 2 * 60 * 1000; // 2分钟
    const messageAge = Date.now() - message.timestamp;

    if (messageAge > recallWindow) {
      return res.status(400).json({ error: '消息发送超过2分钟，无法撤回' });
    }

    // 检查是否已撤回
    if (message.is_recalled) {
      return res.status(400).json({ error: '消息已撤回' });
    }

    // 更新消息状态
    await pool.query(
      'UPDATE messages SET is_recalled = ?, recall_by = ?, recall_time = ? WHERE message_id = ?',
      [true, userId, Date.now(), messageId]
    );

    res.json({ message: '消息已撤回' });
  } catch (error) {
    logger.error('撤回消息失败:', error);
    res.status(500).json({ error: '撤回消息失败' });
  }
});

// 标记消息为已读
router.post('/read', [
  body('conversationId').notEmpty().withMessage('会话ID不能为空'),
  body('messageIds').optional().isArray().withMessage('消息ID列表必须是数组')
], async (req, res) => {
  try {
    const pool = getPool();
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    const { conversationId, messageIds } = req.body;
    const userId = req.user.userId;
    const readAt = Date.now();

    if (messageIds && messageIds.length > 0) {
      // 标记指定消息为已读
      for (const messageId of messageIds) {
        // 检查消息是否属于该会话
        const [messages] = await pool.query(
          'SELECT message_id FROM messages WHERE message_id = ? AND conversation_id = ?',
          [messageId, conversationId]
        );

        if (messages.length > 0) {
          // 插入或更新已读状态
          await pool.query(
            'INSERT INTO message_read_status (message_id, user_id, read_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE read_at = ?',
            [messageId, userId, readAt, readAt]
          );

          // 更新消息状态
          await pool.query(
            'UPDATE messages SET status = ? WHERE message_id = ? AND status != ?',
            ['READ', messageId, 'READ']
          );
        }
      }
    } else {
      // 标记会话中所有未读消息为已读
      await pool.query(
        `UPDATE messages m
         LEFT JOIN message_read_status mrs ON m.message_id = mrs.message_id AND mrs.user_id = ?
         SET m.status = 'READ'
         WHERE m.conversation_id = ? 
         AND m.receiver_id = ?
         AND m.status != 'READ'
         AND mrs.id IS NULL`,
        [userId, conversationId, userId]
      );

      // 批量插入已读状态
      const [unreadMessages] = await pool.query(
        `SELECT message_id FROM messages 
         WHERE conversation_id = ? 
         AND receiver_id = ? 
         AND status = 'READ'
         AND message_id NOT IN (
           SELECT message_id FROM message_read_status WHERE user_id = ?
         )`,
        [conversationId, userId, userId]
      );

      for (const msg of unreadMessages) {
        await pool.query(
          'INSERT INTO message_read_status (message_id, user_id, read_at) VALUES (?, ?, ?)',
          [msg.message_id, userId, readAt]
        );
      }
    }

    // 清除会话未读数
    await pool.query(
      'UPDATE conversations SET unread_count = 0 WHERE conversation_id = ?',
      [conversationId]
    );

    res.json({ message: '消息已标记为已读' });
  } catch (error) {
    logger.error('标记已读失败:', error);
    res.status(500).json({ error: '标记已读失败' });
  }
});

// 删除消息
router.delete('/:messageId', async (req, res) => {
  try {
    const pool = getPool();
    const { messageId } = req.params;
    const userId = req.user.userId;

    logger.info(`用户 ${userId} 请求删除消息 ${messageId}`);

    // 查找消息
    const [messages] = await pool.query(
      'SELECT * FROM messages WHERE message_id = ?',
      [messageId]
    );

    if (messages.length === 0) {
      logger.warn(`消息不存在 - messageId: ${messageId}`);
      return res.status(404).json({ error: '消息不存在' });
    }

    const message = messages[0];

    // 验证权限：只能删除自己发送或接收的消息
    if (message.sender_id !== userId && message.receiver_id !== userId) {
      logger.warn(`用户无权删除此消息 - messageId: ${messageId}, userId: ${userId}, senderId: ${message.sender_id}, receiverId: ${message.receiver_id}`);
      return res.status(403).json({ error: '无权删除此消息' });
    }

    // 删除消息（只删除当前用户接收的消息记录）
    // 注意：对于群消息，每个接收者都有自己的记录，所以只删除当前用户的记录
    let deleteResult;
    
    if (message.sender_id === userId) {
      // 发送者删除自己发送的消息：删除所有接收者记录中属于当前用户的记录
      // 对于单聊：删除接收者的记录
      // 对于群聊：删除当前用户作为接收者的记录
      [deleteResult] = await pool.query(
        'DELETE FROM messages WHERE message_id = ? AND receiver_id = ?',
        [messageId, userId]
      );
      logger.info(`发送者删除自己发送的消息 - messageId: ${messageId}, userId: ${userId}, 删除了 ${deleteResult.affectedRows} 条记录`);
    } else {
      // 接收者删除接收到的消息：只删除自己的记录
      [deleteResult] = await pool.query(
        'DELETE FROM messages WHERE message_id = ? AND receiver_id = ?',
        [messageId, userId]
      );
      logger.info(`接收者删除接收到的消息 - messageId: ${messageId}, userId: ${userId}, 删除了 ${deleteResult.affectedRows} 条记录`);
    }

    if (deleteResult.affectedRows === 0) {
      logger.warn(`消息删除影响行数为0 - messageId: ${messageId}, userId: ${userId}, senderId: ${message.sender_id}, receiverId: ${message.receiver_id}`);
    }

    logger.info(`用户 ${userId} 删除了消息 ${messageId} - 删除了 ${deleteResult.affectedRows} 条记录`);
    res.json({ message: '消息已删除' });
  } catch (error) {
    logger.error('删除消息失败:', error);
    res.status(500).json({ error: '删除消息失败' });
  }
});

// 获取离线消息
router.get('/offline', async (req, res) => {
  try {
    const pool = getPool();
    const userId = req.user.userId;
    const { lastMessageTime } = req.query;
    const since = lastMessageTime ? parseInt(lastMessageTime) : 0;

    const [messages] = await pool.query(
      `SELECT m.*, 
              CASE WHEN mrs.message_id IS NOT NULL THEN 1 ELSE 0 END as is_read
       FROM messages m
       LEFT JOIN message_read_status mrs ON m.message_id = mrs.message_id AND mrs.user_id = ?
       WHERE m.receiver_id = ? 
       AND m.timestamp > ?
       ORDER BY m.timestamp ASC
       LIMIT 100`,
      [userId, userId, since]
    );

    res.json(messages);
  } catch (error) {
    logger.error('获取离线消息失败:', error);
    res.status(500).json({ error: '获取离线消息失败' });
  }
});

module.exports = router;

