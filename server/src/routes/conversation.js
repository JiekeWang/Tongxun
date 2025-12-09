const express = require('express');
const { getPool } = require('../config/database');
const { authenticateToken } = require('../middleware/auth');
const logger = require('../utils/logger');

const router = express.Router();

// 所有路由需要认证
router.use(authenticateToken);

// 获取会话列表
router.get('/', async (req, res) => {
  try {
    const pool = getPool();
    const userId = req.user.userId;

    // 获取用户的所有会话（包括单聊和群聊）
    // 单聊：target_id 是对方的 user_id，且 target_id != userId
    // 群聊：conversation_id 是 group_id，需要检查用户是否是群成员
    const [conversations] = await pool.query(
      `SELECT 
        c.conversation_id,
        c.type,
        c.target_id,
        c.target_name,
        c.target_avatar,
        c.last_message,
        c.last_message_time,
        c.unread_count,
        c.is_top,
        c.is_muted,
        c.updated_at
      FROM conversations c
      WHERE (c.type = 'SINGLE' AND c.target_id != ?)
         OR (c.type = 'GROUP' AND c.conversation_id IN (
           SELECT gm.group_id FROM group_members gm WHERE gm.user_id = ?
         ))
      ORDER BY c.is_top DESC, c.last_message_time DESC`,
      [userId, userId]
    );

    logger.info(`用户 ${userId} 获取会话列表 - 共 ${conversations.length} 个会话`);
    res.json(conversations);
  } catch (error) {
    logger.error('获取会话列表失败:', error);
    res.status(500).json({ error: '获取会话列表失败' });
  }
});

// 删除会话
router.delete('/:conversationId', async (req, res) => {
  try {
    const pool = getPool();
    const userId = req.user.userId;
    const { conversationId } = req.params;

    logger.info(`用户 ${userId} 请求删除会话 ${conversationId}`);

    // 先检查会话是否存在
    const [conversations] = await pool.query(
      `SELECT conversation_id, type, target_id FROM conversations 
       WHERE conversation_id = ?`,
      [conversationId]
    );

    if (conversations.length === 0) {
      logger.warn(`会话不存在 - conversationId: ${conversationId}`);
      return res.status(404).json({ error: '会话不存在' });
    }

    const conversation = conversations[0];
    let deleteQuery;
    let deleteParams;
    
    if (conversation.type === 'SINGLE') {
      // 单聊：检查 target_id 是否是对方用户ID（确保删除的是自己的记录，target_id 应该是对方用户ID）
      // 单聊的 conversation_id 是 "user1_user2" 格式，每个用户有自己的记录
      // 当前用户的记录：target_id = 对方用户ID（!= userId）
      // 对方用户的记录：target_id = 当前用户ID (= userId)
      // 我们要删除的是当前用户的记录，所以 target_id != userId
      if (conversation.target_id === userId) {
        logger.warn(`这是对方用户的单聊会话记录，无权删除 - conversationId: ${conversationId}, userId: ${userId}`);
        return res.status(403).json({ error: '无权删除此会话' });
      }
      deleteQuery = 'DELETE FROM conversations WHERE conversation_id = ? AND target_id = ?';
      deleteParams = [conversationId, conversation.target_id];
      logger.info(`删除单聊会话 - conversationId: ${conversationId}, targetId: ${conversation.target_id}`);
    } else if (conversation.type === 'GROUP') {
      // 群聊：检查用户是否是群成员
      const [groupCheck] = await pool.query(
        `SELECT gm.user_id FROM group_members gm 
         WHERE gm.group_id = ? AND gm.user_id = ?`,
        [conversationId, userId]
      );

      if (groupCheck.length === 0) {
        logger.warn(`用户不是群成员，无权删除群聊会话 - conversationId: ${conversationId}, userId: ${userId}`);
        return res.status(403).json({ error: '无权删除此会话' });
      }

      // 群聊：由于是共享记录（所有成员共享一个 conversation_id），删除会影响所有成员
      // 但根据需求，用户删除会话应该从列表中移除
      deleteQuery = 'DELETE FROM conversations WHERE conversation_id = ?';
      deleteParams = [conversationId];
      logger.info(`删除群聊会话 - conversationId: ${conversationId} (注意：这会删除所有成员的会话记录)`);
    } else {
      return res.status(400).json({ error: '未知的会话类型' });
    }
    
    const [deleteResult] = await pool.query(deleteQuery, deleteParams);

    // 同时删除该会话的所有消息（只删除当前用户接收的消息）
    const [deleteMessagesResult] = await pool.query(
      'DELETE FROM messages WHERE conversation_id = ? AND receiver_id = ?',
      [conversationId, userId]
    );

    logger.info(`用户 ${userId} 删除了会话 ${conversationId} - 删除了 ${deleteMessagesResult.affectedRows} 条消息`);
    res.json({ message: '会话已删除' });
  } catch (error) {
    logger.error('删除会话失败:', error);
    res.status(500).json({ error: '删除会话失败' });
  }
});

module.exports = router;

