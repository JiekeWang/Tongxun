const express = require('express');
const { authenticateToken } = require('../middleware/auth');
const { getPool } = require('../config/database');
const { v4: uuidv4 } = require('uuid');
const logger = require('../utils/logger');

const router = express.Router();

// 所有路由需要认证
router.use(authenticateToken);

// 创建群组
router.post('/create', async (req, res) => {
  try {
    const pool = getPool();
    const { groupName, description, memberIds } = req.body;
    const ownerId = req.user.userId;

    logger.info('创建群组请求:', { groupName, description, memberIds, ownerId });

    if (!groupName) {
      logger.warn('创建群组失败: 群组名称不能为空', { body: req.body });
      return res.status(400).json({ error: '群组名称不能为空' });
    }

    // 验证：群总人数（包含自己）至少需要3人
    const allMemberIds = [ownerId, ...(memberIds || [])];
    if (allMemberIds.length < 3) {
      logger.warn('创建群组失败: 群组至少需要3人（包含创建者）', { 
        ownerId, 
        memberIds, 
        totalCount: allMemberIds.length 
      });
      return res.status(400).json({ error: '群组至少需要3人（包含创建者）' });
    }

    const groupId = uuidv4();

    // 创建群组
    await pool.query(
      `INSERT INTO \`groups\` (group_id, group_name, description, owner_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [groupId, groupName, description || null, ownerId, Date.now(), Date.now()]
    );

    // 添加群成员（包括群主）
    // 注意：allMemberIds 已在上面验证时计算过了
    for (const memberId of allMemberIds) {
      await pool.query(
        `INSERT INTO group_members (group_id, user_id, role, joined_at)
         VALUES (?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE role = VALUES(role)`,
        [groupId, memberId, memberId === ownerId ? 'OWNER' : 'MEMBER', Date.now()]
      );
    }

    // 更新群成员数量
    await pool.query(
      `UPDATE \`groups\` SET member_count = (SELECT COUNT(*) FROM group_members WHERE group_id = ?) WHERE group_id = ?`,
      [groupId, groupId]
    );

    res.json({
      groupId,
      groupName,
      description,
      ownerId,
      memberCount: allMemberIds.length
    });
  } catch (error) {
    logger.error('创建群组失败:', error);
    res.status(500).json({ error: '创建群组失败' });
  }
});

// 加入群组
router.post('/:groupId/join', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;
    const userId = req.user.userId;

    // 检查群组是否存在
    const [groups] = await pool.query(
      'SELECT * FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    if (groups.length === 0) {
      return res.status(404).json({ error: '群组不存在' });
    }

    const group = groups[0];

    // 检查是否已经是成员
    const [members] = await pool.query(
      'SELECT * FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    if (members.length > 0) {
      return res.status(400).json({ error: '已经是群成员' });
    }

    // 检查群组是否已满
    if (group.member_count >= group.max_member_count) {
      return res.status(400).json({ error: '群组已满' });
    }

    // 添加成员
    await pool.query(
      `INSERT INTO group_members (group_id, user_id, role, joined_at)
       VALUES (?, ?, 'MEMBER', ?)`,
      [groupId, userId, Date.now()]
    );

    // 更新群成员数量
    await pool.query(
      `UPDATE \`groups\` SET member_count = member_count + 1 WHERE group_id = ?`,
      [groupId]
    );

    res.json({ success: true });
  } catch (error) {
    logger.error('加入群组失败:', error);
    res.status(500).json({ error: '加入群组失败' });
  }
});

// 退出群组
router.post('/:groupId/leave', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;
    const userId = req.user.userId;

    // 检查是否是群主
    const [groups] = await pool.query(
      'SELECT owner_id FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    if (groups.length === 0) {
      return res.status(404).json({ error: '群组不存在' });
    }

    if (groups[0].owner_id === userId) {
      return res.status(400).json({ error: '群主不能退出群组' });
    }

    // 删除成员
    await pool.query(
      'DELETE FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    // 更新群成员数量
    await pool.query(
      `UPDATE \`groups\` SET member_count = member_count - 1 WHERE group_id = ?`,
      [groupId]
    );

    res.json({ success: true });
  } catch (error) {
    logger.error('退出群组失败:', error);
    res.status(500).json({ error: '退出群组失败' });
  }
});

// 获取群组信息
router.get('/:groupId', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;

    const [groups] = await pool.query(
      'SELECT * FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    if (groups.length === 0) {
      return res.status(404).json({ error: '群组不存在' });
    }

    const group = groups[0];

    // 获取群成员列表
    const [members] = await pool.query(
      `SELECT gm.user_id, gm.role, u.nickname, u.avatar
       FROM group_members gm
       JOIN users u ON gm.user_id = u.user_id
       WHERE gm.group_id = ?
       ORDER BY gm.joined_at ASC`,
      [groupId]
    );

    res.json({
      groupId: group.group_id,
      groupName: group.group_name,
      description: group.description,
      avatar: group.avatar,
      ownerId: group.owner_id,
      memberCount: group.member_count,
      maxMemberCount: group.max_member_count,
      createdAt: group.created_at,
      members: members.map(m => ({
        userId: m.user_id,
        nickname: m.nickname,
        avatar: m.avatar,
        role: m.role
      }))
    });
  } catch (error) {
    logger.error('获取群组信息失败:', error);
    res.status(500).json({ error: '获取群组信息失败' });
  }
});

// 获取群成员列表
router.get('/:groupId/members', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;

    const [members] = await pool.query(
      `SELECT gm.user_id, gm.role, u.nickname, u.avatar
       FROM group_members gm
       JOIN users u ON gm.user_id = u.user_id
       WHERE gm.group_id = ?
       ORDER BY gm.joined_at ASC`,
      [groupId]
    );

    res.json(members.map(m => ({
      userId: m.user_id,
      nickname: m.nickname,
      avatar: m.avatar,
      role: m.role
    })));
  } catch (error) {
    logger.error('获取群成员列表失败:', error);
    res.status(500).json({ error: '获取群成员列表失败' });
  }
});

// 搜索群组
router.get('/search', async (req, res) => {
  try {
    const pool = getPool();
    const { keyword } = req.query;

    if (!keyword || keyword.trim() === '') {
      return res.status(400).json({ error: '搜索关键词不能为空' });
    }

    const searchKeyword = `%${keyword.trim()}%`;
    
    const [groups] = await pool.query(
      `SELECT g.*, u.nickname as owner_name
       FROM \`groups\` g
       LEFT JOIN users u ON g.owner_id = u.user_id
       WHERE g.group_name LIKE ? OR g.description LIKE ?
       ORDER BY g.created_at DESC
       LIMIT 50`,
      [searchKeyword, searchKeyword]
    );

    res.json(groups.map(g => ({
      groupId: g.group_id,
      groupName: g.group_name,
      description: g.description,
      avatar: g.avatar,
      ownerId: g.owner_id,
      ownerName: g.owner_name,
      memberCount: g.member_count,
      maxMemberCount: g.max_member_count,
      createdAt: g.created_at
    })));
  } catch (error) {
    logger.error('搜索群组失败:', error);
    res.status(500).json({ error: '搜索群组失败' });
  }
});

// 申请加入群组
router.post('/:groupId/apply', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;
    const userId = req.user.userId;
    const { message } = req.body;

    // 检查群组是否存在
    const [groups] = await pool.query(
      'SELECT * FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    if (groups.length === 0) {
      return res.status(404).json({ error: '群组不存在' });
    }

    // 检查是否已经是成员
    const [members] = await pool.query(
      'SELECT * FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    if (members.length > 0) {
      return res.status(400).json({ error: '已经是群成员' });
    }

    // 检查是否已经有待处理的申请
    const [existingRequests] = await pool.query(
      'SELECT * FROM group_join_requests WHERE group_id = ? AND user_id = ? AND status = "PENDING"',
      [groupId, userId]
    );

    if (existingRequests.length > 0) {
      return res.status(400).json({ error: '已提交申请，请等待审核' });
    }

    const requestId = uuidv4();

    // 创建申请记录
    await pool.query(
      `INSERT INTO group_join_requests (request_id, group_id, user_id, message, status, created_at)
       VALUES (?, ?, ?, ?, 'PENDING', ?)`,
      [requestId, groupId, userId, message || null, Date.now()]
    );

    res.json({ requestId, success: true });
  } catch (error) {
    logger.error('申请加入群组失败:', error);
    res.status(500).json({ error: '申请加入群组失败' });
  }
});

// 获取群组申请列表（群主或管理员）
router.get('/:groupId/requests', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;
    const userId = req.user.userId;

    // 检查权限（群主或管理员）
    const [members] = await pool.query(
      'SELECT role FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    if (members.length === 0 || (members[0].role !== 'OWNER' && members[0].role !== 'ADMIN')) {
      return res.status(403).json({ error: '无权查看申请列表' });
    }

    const [requests] = await pool.query(
      `SELECT gjr.request_id, gjr.user_id, gjr.message, gjr.status, gjr.created_at, u.nickname, u.avatar
       FROM group_join_requests gjr
       JOIN users u ON gjr.user_id = u.user_id
       WHERE gjr.group_id = ? AND gjr.status = 'PENDING'
       ORDER BY gjr.created_at DESC`,
      [groupId]
    );

    res.json(requests.map(r => ({
      requestId: r.request_id,
      userId: r.user_id,
      nickname: r.nickname,
      avatar: r.avatar,
      message: r.message,
      status: r.status,
      createdAt: r.created_at
    })));
  } catch (error) {
    logger.error('获取申请列表失败:', error);
    res.status(500).json({ error: '获取申请列表失败' });
  }
});

// 审核群组申请
router.post('/:groupId/requests/:requestId/approve', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId, requestId } = req.params;
    const userId = req.user.userId;

    // 检查权限
    const [members] = await pool.query(
      'SELECT role FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    if (members.length === 0 || (members[0].role !== 'OWNER' && members[0].role !== 'ADMIN')) {
      return res.status(403).json({ error: '无权审核申请' });
    }

    // 获取申请信息
    const [requests] = await pool.query(
      'SELECT * FROM group_join_requests WHERE request_id = ? AND group_id = ?',
      [requestId, groupId]
    );

    if (requests.length === 0) {
      return res.status(404).json({ error: '申请不存在' });
    }

    const request = requests[0];
    if (request.status !== 'PENDING') {
      return res.status(400).json({ error: '申请已处理' });
    }

    // 检查群组是否已满
    const [groups] = await pool.query(
      'SELECT member_count, max_member_count FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    if (groups.length === 0) {
      return res.status(404).json({ error: '群组不存在' });
    }

    const group = groups[0];
    if (group.member_count >= group.max_member_count) {
      // 更新申请状态为拒绝
      await pool.query(
        'UPDATE group_join_requests SET status = ?, processed_at = ? WHERE request_id = ?',
        ['REJECTED', Date.now(), requestId]
      );
      return res.status(400).json({ error: '群组已满' });
    }

    // 添加成员
    await pool.query(
      `INSERT INTO group_members (group_id, user_id, role, joined_at)
       VALUES (?, ?, 'MEMBER', ?)`,
      [groupId, request.user_id, Date.now()]
    );

    // 更新群成员数量
    await pool.query(
      `UPDATE \`groups\` SET member_count = member_count + 1 WHERE group_id = ?`,
      [groupId]
    );

    // 更新申请状态
    await pool.query(
      'UPDATE group_join_requests SET status = ?, processed_at = ? WHERE request_id = ?',
      ['APPROVED', Date.now(), requestId]
    );

    res.json({ success: true });
  } catch (error) {
    logger.error('审核申请失败:', error);
    res.status(500).json({ error: '审核申请失败' });
  }
});

// 拒绝群组申请
router.post('/:groupId/requests/:requestId/reject', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId, requestId } = req.params;
    const userId = req.user.userId;

    // 检查权限
    const [members] = await pool.query(
      'SELECT role FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    if (members.length === 0 || (members[0].role !== 'OWNER' && members[0].role !== 'ADMIN')) {
      return res.status(403).json({ error: '无权审核申请' });
    }

    // 更新申请状态
    await pool.query(
      'UPDATE group_join_requests SET status = ?, processed_at = ? WHERE request_id = ?',
      ['REJECTED', Date.now(), requestId]
    );

    res.json({ success: true });
  } catch (error) {
    logger.error('拒绝申请失败:', error);
    res.status(500).json({ error: '拒绝申请失败' });
  }
});

// 添加成员到群组（群主或管理员）
router.post('/:groupId/members', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;
    const { memberIds } = req.body;
    const userId = req.user.userId;

    if (!memberIds || !Array.isArray(memberIds) || memberIds.length === 0) {
      return res.status(400).json({ error: '成员ID列表不能为空' });
    }

    // 检查权限
    const [members] = await pool.query(
      'SELECT role FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    if (members.length === 0 || (members[0].role !== 'OWNER' && members[0].role !== 'ADMIN')) {
      return res.status(403).json({ error: '无权添加成员' });
    }

    // 检查群组是否存在
    const [groups] = await pool.query(
      'SELECT member_count, max_member_count FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    if (groups.length === 0) {
      return res.status(404).json({ error: '群组不存在' });
    }

    const group = groups[0];

    // 过滤掉已经是成员的ID
    const [existingMembers] = await pool.query(
      'SELECT user_id FROM group_members WHERE group_id = ? AND user_id IN (?)',
      [groupId, memberIds]
    );

    const existingMemberIds = existingMembers.map(m => m.user_id);
    const newMemberIds = memberIds.filter(id => !existingMemberIds.includes(id));

    if (newMemberIds.length === 0) {
      return res.status(400).json({ error: '所有用户都已是群成员' });
    }

    // 检查是否会超出最大成员数
    if (group.member_count + newMemberIds.length > group.max_member_count) {
      return res.status(400).json({ error: `只能添加 ${group.max_member_count - group.member_count} 个成员` });
    }

    // 批量添加成员
    for (const memberId of newMemberIds) {
      await pool.query(
        `INSERT INTO group_members (group_id, user_id, role, joined_at)
         VALUES (?, ?, 'MEMBER', ?)`,
        [groupId, memberId, Date.now()]
      );
    }

    // 更新群成员数量
    await pool.query(
      `UPDATE \`groups\` SET member_count = member_count + ? WHERE group_id = ?`,
      [newMemberIds.length, groupId]
    );

    res.json({ success: true, addedCount: newMemberIds.length });
  } catch (error) {
    logger.error('添加成员失败:', error);
    res.status(500).json({ error: '添加成员失败' });
  }
});

// 删除群成员（群主或管理员）
router.delete('/:groupId/members/:memberId', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId, memberId } = req.params;
    const userId = req.user.userId;

    // 检查权限（群主或管理员）
    const [members] = await pool.query(
      'SELECT role FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, userId]
    );

    if (members.length === 0 || (members[0].role !== 'OWNER' && members[0].role !== 'ADMIN')) {
      return res.status(403).json({ error: '无权删除成员' });
    }

    // 检查要删除的成员是否存在
    const [targetMember] = await pool.query(
      'SELECT role FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, memberId]
    );

    if (targetMember.length === 0) {
      return res.status(404).json({ error: '成员不存在' });
    }

    // 不能删除群主
    if (targetMember[0].role === 'OWNER') {
      return res.status(400).json({ error: '不能删除群主' });
    }

    // 删除成员
    await pool.query(
      'DELETE FROM group_members WHERE group_id = ? AND user_id = ?',
      [groupId, memberId]
    );

    // 更新群成员数量
    await pool.query(
      `UPDATE \`groups\` SET member_count = member_count - 1 WHERE group_id = ?`,
      [groupId]
    );

    res.json({ success: true });
  } catch (error) {
    logger.error('删除成员失败:', error);
    res.status(500).json({ error: '删除成员失败' });
  }
});

// 解散群组（仅群主）
router.delete('/:groupId', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId } = req.params;
    const userId = req.user.userId;

    // 检查是否是群主
    const [groups] = await pool.query(
      'SELECT owner_id FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    if (groups.length === 0) {
      return res.status(404).json({ error: '群组不存在' });
    }

    if (groups[0].owner_id !== userId) {
      return res.status(403).json({ error: '只有群主可以解散群组' });
    }

    // 删除所有群成员
    await pool.query(
      'DELETE FROM group_members WHERE group_id = ?',
      [groupId]
    );

    // 删除群组
    await pool.query(
      'DELETE FROM `groups` WHERE group_id = ?',
      [groupId]
    );

    // 删除群组相关的申请记录
    await pool.query(
      'DELETE FROM group_join_requests WHERE group_id = ?',
      [groupId]
    );

    res.json({ success: true });
  } catch (error) {
    logger.error('解散群组失败:', error);
    res.status(500).json({ error: '解散群组失败' });
  }
});

// 获取群聊已读人数统计
router.get('/:groupId/messages/:messageId/readers', async (req, res) => {
  try {
    const pool = getPool();
    const { groupId, messageId } = req.params;

    // 获取已读该消息的用户列表
    const [readers] = await pool.query(
      `SELECT mrs.user_id, u.nickname, u.avatar, mrs.read_at
       FROM message_read_status mrs
       JOIN users u ON mrs.user_id = u.user_id
       WHERE mrs.message_id = ?
       ORDER BY mrs.read_at ASC`,
      [messageId]
    );

    // 获取群成员总数
    const [memberCount] = await pool.query(
      'SELECT COUNT(*) as count FROM group_members WHERE group_id = ?',
      [groupId]
    );

    res.json({
      messageId,
      readCount: readers.length,
      totalCount: memberCount[0].count,
      readers: readers.map(r => ({
        userId: r.user_id,
        nickname: r.nickname,
        avatar: r.avatar,
        readAt: r.read_at
      }))
    });
  } catch (error) {
    logger.error('获取已读人数统计失败:', error);
    res.status(500).json({ error: '获取已读人数统计失败' });
  }
});

module.exports = router;

