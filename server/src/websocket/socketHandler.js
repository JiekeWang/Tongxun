const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const { getPool } = require('../config/database');
const {
  setUserOnline,
  setUserOffline,
  addSocketConnection,
  removeSocketConnection,
  getUserSockets
} = require('../config/redis');
const logger = require('../utils/logger');

// 存储在线用户连接
const userSockets = new Map(); // userId -> Set of socketIds
let ioInstance = null;

function setupWebSocket(io) {
  ioInstance = io;
  // 认证中间件
  io.use(async (socket, next) => {
    try {
      const token = socket.handshake.query.token;

      if (!token) {
        return next(new Error('未提供认证令牌'));
      }

      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      socket.userId = decoded.userId;
      socket.userInfo = decoded;
      next();
    } catch (error) {
      logger.error('WebSocket认证失败:', error);
      next(new Error('认证失败'));
    }
  });

  io.on('connection', async (socket) => {
    const userId = socket.userId;
    logger.info(`用户 ${userId} 连接WebSocket: ${socket.id}, handshake: ${JSON.stringify(socket.handshake.query)}`);

    // 单设备登录检查：如果用户已经在其他设备连接，先断开所有旧连接
    // 1. 检查内存中的连接
    const existingSockets = userSockets.get(userId);
    if (existingSockets && existingSockets.size > 0) {
      logger.warn(`[单设备登录] 检测到用户 ${userId} 已有 ${existingSockets.size} 个活跃连接（内存），开始断开旧连接`);
      
      // 断开所有旧连接（不包括当前这个刚连接的socket）
      // 先发送事件，然后延迟断开，确保客户端有时间处理事件
      const disconnectPromises = Array.from(existingSockets)
        .filter(existingSocketId => existingSocketId !== socket.id)
        .map(existingSocketId => {
          return new Promise((resolve) => {
            const existingSocket = ioInstance.sockets.sockets.get(existingSocketId);
            if (existingSocket && existingSocket.connected) {
              try {
                // 先发送账号被踢事件
                existingSocket.emit('account_kicked', {
                  reason: '账号在其他设备登录',
                  message: '您的账号在其他设备登录，当前设备已下线',
                  timestamp: Date.now()
                });
                logger.info(`[单设备登录] 已发送account_kicked事件 - userId: ${userId}, socketId: ${existingSocketId}`);
                
                // 延迟断开，给客户端时间处理事件（200ms应该足够）
                setTimeout(() => {
                  try {
                    if (existingSocket.connected) {
                      existingSocket.disconnect(true);
                      logger.info(`[单设备登录] 已断开用户旧连接（内存）- userId: ${userId}, socketId: ${existingSocketId}`);
                    }
                  } catch (error) {
                    logger.error(`断开旧连接失败（内存）- userId: ${userId}, socketId: ${existingSocketId}`, error);
                  }
                  resolve();
                }, 200);
              } catch (error) {
                logger.error(`发送account_kicked事件失败（内存）- userId: ${userId}, socketId: ${existingSocketId}`, error);
                resolve();
              }
            } else {
              resolve();
            }
          });
        });
      
      // 等待所有断开操作完成（但最多等待500ms）
      await Promise.race([
        Promise.all(disconnectPromises),
        new Promise(resolve => setTimeout(resolve, 500))
      ]);
    }

    // 2. 检查Redis中的连接（可能包含其他服务器实例的连接）
    try {
      const redisSockets = await getUserSockets(userId);
      if (redisSockets && redisSockets.length > 0) {
        logger.warn(`[单设备登录] 检测到用户 ${userId} 在Redis中有 ${redisSockets.length} 个连接记录，开始清理`);
        
        // 先发送事件，然后延迟断开，确保客户端有时间处理事件
        const redisDisconnectPromises = redisSockets
          .filter(redisSocketId => redisSocketId !== socket.id)
          .map(redisSocketId => {
            return new Promise(async (resolve) => {
              const redisSocket = ioInstance.sockets.sockets.get(redisSocketId);
              if (redisSocket && redisSocket.connected) {
                try {
                  // 先发送账号被踢事件
                  redisSocket.emit('account_kicked', {
                    reason: '账号在其他设备登录',
                    message: '您的账号在其他设备登录，当前设备已下线',
                    timestamp: Date.now()
                  });
                  logger.info(`[单设备登录] 已发送account_kicked事件（Redis）- userId: ${userId}, socketId: ${redisSocketId}`);
                  
                  // 延迟断开，给客户端时间处理事件（200ms应该足够）
                  setTimeout(() => {
                    try {
                      if (redisSocket.connected) {
                        redisSocket.disconnect(true);
                        logger.info(`[单设备登录] 已断开用户旧连接（Redis）- userId: ${userId}, socketId: ${redisSocketId}`);
                      }
                    } catch (error) {
                      logger.error(`断开旧连接失败（Redis）- userId: ${userId}, socketId: ${redisSocketId}`, error);
                    }
                    
                    // 从Redis中移除旧连接记录
                    removeSocketConnection(userId, redisSocketId).catch(err => {
                      logger.warn(`清理Redis中的旧连接记录失败 - userId: ${userId}, socketId: ${redisSocketId}`, err);
                    });
                    
                    resolve();
                  }, 200);
                } catch (error) {
                  logger.error(`发送account_kicked事件失败（Redis）- userId: ${userId}, socketId: ${redisSocketId}`, error);
                  // 从Redis中移除旧连接记录
                  await removeSocketConnection(userId, redisSocketId).catch(() => {});
                  resolve();
                }
              } else {
                // 从Redis中移除旧连接记录
                await removeSocketConnection(userId, redisSocketId).catch(() => {});
                resolve();
    }
            });
          });
        
        // 等待所有断开操作完成（但最多等待500ms）
        await Promise.race([
          Promise.all(redisDisconnectPromises),
          new Promise(resolve => setTimeout(resolve, 500))
        ]);
      }
    } catch (error) {
      logger.warn(`从Redis获取用户连接记录失败 - userId: ${userId}`, error);
    }

    // 添加到在线用户（单设备登录：确保只有当前这个连接）
    // 清空并重新设置，只保留当前连接
    userSockets.set(userId, new Set([socket.id]));
    
    // 更新Redis：先清理旧的，再添加新的
    try {
      // 清理Redis中该用户的所有旧连接记录
      const oldRedisSockets = await getUserSockets(userId);
      if (oldRedisSockets && oldRedisSockets.length > 0) {
        for (const oldSocketId of oldRedisSockets) {
          if (oldSocketId !== socket.id) {
            await removeSocketConnection(userId, oldSocketId).catch(() => {});
          }
        }
      }
    } catch (error) {
      logger.warn(`清理Redis旧连接记录时出错 - userId: ${userId}`, error);
    }
    
    // 添加当前连接到Redis
    await addSocketConnection(userId, socket.id);
    await setUserOnline(userId, socket.id);

    // 发送连接成功消息
    socket.emit('connected', {
      message: '连接成功',
      userId: userId
    });

    // 心跳处理（Socket.IO会自动处理ping/pong，这里处理应用层的心跳）
    socket.on('ping', () => {
      socket.emit('pong', { timestamp: Date.now() });
    });

    // 接收消息
    socket.on('message', async (data) => {
      try {
        logger.info(`🔥 收到message事件 - socketId: ${socket.id}, userId: ${userId}, data: ${JSON.stringify(data).substring(0, 200)}`);
        await handleMessage(socket, data);
      } catch (error) {
        logger.error('❌ 处理消息失败:', error);
        socket.emit('error', { message: '消息发送失败' });
      }
    });

    // 消息撤回
    socket.on('recall_message', async (data) => {
      try {
        await handleRecallMessage(socket, data);
      } catch (error) {
        logger.error('处理撤回消息失败:', error);
        socket.emit('error', { message: '撤回消息失败' });
      }
    });

    // 视频通话信令处理
    socket.on('video_call', async (data) => {
      try {
        await handleVideoCall(socket, data);
      } catch (error) {
        logger.error('处理视频通话请求失败:', error);
        socket.emit('error', { message: '视频通话请求失败' });
      }
    });

    // 语音通话信令处理
    socket.on('voice_call', async (data) => {
      try {
        await handleVoiceCall(socket, data);
      } catch (error) {
        logger.error('处理语音通话请求失败:', error);
        socket.emit('error', { message: '语音通话请求失败' });
      }
    });

    socket.on('video_call_sdp', async (data) => {
      try {
        await handleVideoCallSdp(socket, data);
      } catch (error) {
        logger.error('处理视频通话SDP失败:', error);
        socket.emit('error', { message: '视频通话SDP处理失败' });
      }
    });

    socket.on('video_call_ice', async (data) => {
      try {
        await handleVideoCallIce(socket, data);
      } catch (error) {
        logger.error('处理视频通话ICE Candidate失败:', error);
        socket.emit('error', { message: '视频通话ICE Candidate处理失败' });
      }
    });

    socket.on('video_call_answer', async (data) => {
      try {
        await handleVideoCallAnswer(socket, data);
      } catch (error) {
        logger.error('处理视频通话接听失败:', error);
        socket.emit('error', { message: '视频通话接听失败' });
      }
    });

    socket.on('video_call_reject', async (data) => {
      try {
        await handleVideoCallReject(socket, data);
      } catch (error) {
        logger.error('处理视频通话拒绝失败:', error);
        socket.emit('error', { message: '视频通话拒绝失败' });
      }
    });

    socket.on('video_call_hangup', async (data) => {
      try {
        await handleVideoCallHangup(socket, data);
      } catch (error) {
        logger.error('处理视频通话挂断失败:', error);
        socket.emit('error', { message: '视频通话挂断失败' });
      }
    });

    // 断开连接（合并处理，避免重复）
    socket.on('disconnect', (reason) => {
      logger.info(`用户 ${userId} 断开WebSocket连接: ${socket.id}, 原因: ${reason}`);
      
      // 从内存中清理
      if (userSockets.has(userId)) {
        userSockets.get(userId).delete(socket.id);
        if (userSockets.get(userId).size === 0) {
          userSockets.delete(userId);
          setUserOffline(userId).catch(err => {
            logger.warn(`设置用户离线状态失败 - userId: ${userId}`, err);
          });
        }
      }
      
      // 从Redis中清理
      removeSocketConnection(userId, socket.id).catch(err => {
        logger.warn(`清理Redis中的socket连接失败 - userId: ${userId}, socketId: ${socket.id}`, err);
      });
    });
    
    // 处理连接错误
    socket.on('error', (error) => {
      logger.error(`用户 ${userId} WebSocket错误:`, error);
    });
  });
}

// 处理消息
async function handleMessage(socket, data) {
  const { messageId, conversationId, receiverId, content, messageType, timestamp, extra } = data;
  const senderId = socket.userId;

  logger.info(`收到消息 - senderId: ${senderId}, receiverId: ${receiverId}, messageId: ${messageId}, conversationId: ${conversationId}, content: ${content?.substring(0, 50)}`);

  // 验证必需字段
  if (!messageId || !conversationId || !content) {
    logger.error(`消息格式不正确 - messageId: ${messageId}, conversationId: ${conversationId}, content存在: ${!!content}`);
    socket.emit('error', { message: '消息格式不正确' });
    return;
  }

  const pool = getPool();
  
  // 判断是单聊还是群聊（单聊的conversationId格式是 userA_userB，群聊是groupId）
  // 检查conversationId是否是群组ID（查询groups表）
  const [groups] = await pool.query(
    'SELECT group_id FROM `groups` WHERE group_id = ?',
    [conversationId]
  );
  
  const isGroupMessage = groups.length > 0;
  
  logger.info(`消息类型判断 - conversationId: ${conversationId}, isGroupMessage: ${isGroupMessage}`);

  // 构建消息数据
  const messageData = {
    messageId,
    conversationId,
    senderId,
    receiverId: receiverId || conversationId, // 群消息时receiverId可以是groupId
    content,
    messageType,
    timestamp,
    extra
  };

  if (isGroupMessage) {
    // 群消息：发送给所有群成员（除了发送者）
    logger.info(`📤 准备推送群消息 - groupId: ${conversationId}, messageId: ${messageId}`);
    
    try {
      // 获取群成员列表
      const [members] = await pool.query(
        'SELECT user_id FROM group_members WHERE group_id = ?',
        [conversationId]
      );
      
      logger.info(`群成员数量: ${members.length}, 发送者: ${senderId}`);
      
      // 验证发送者是否是群成员
      const isMember = members.some(m => m.user_id === senderId);
      if (!isMember) {
        socket.emit('error', { message: '您不是群成员，无法发送消息' });
        return;
      }
      
      // 发送给所有成员（除了发送者）
      const sendPromises = members
        .filter(m => m.user_id !== senderId)
        .map(member => {
          // 为每个接收者设置receiverId
          const memberMessageData = { ...messageData, receiverId: member.user_id };
          return sendToUser(member.user_id, 'message', memberMessageData)
            .catch(error => {
              logger.error(`发送群消息给成员失败 - userId: ${member.user_id}, messageId: ${messageId}`, error);
            });
        });
      
      Promise.all(sendPromises)
        .then(() => {
          logger.info(`✅ 群消息推送完成 - groupId: ${conversationId}, messageId: ${messageId}, 已发送给 ${sendPromises.length} 个成员`);
        });
  } catch (error) {
      logger.error(`获取群成员失败 - groupId: ${conversationId}`, error);
      socket.emit('error', { message: '获取群成员失败' });
      return;
    }
  } else {
    // 单聊：发送给单个接收者
    if (!receiverId) {
      logger.error(`单聊消息缺少receiverId - messageId: ${messageId}`);
      socket.emit('error', { message: '单聊消息必须指定接收者' });
    return;
  }

    logger.info(`📤 准备推送单聊消息给接收者 - receiverId: ${receiverId}, messageId: ${messageId}`);
    
    sendToUser(receiverId, 'message', messageData)
      .then(() => {
        logger.info(`✅ 单聊消息推送完成 - receiverId: ${receiverId}, messageId: ${messageId}`);
      })
      .catch(error => {
        logger.error(`❌ 发送消息给接收者失败 - messageId: ${messageId}, receiverId: ${receiverId}`, error);
      });
  }

  // 发送消息确认给发送者
  socket.emit('message_sent', {
    messageId,
    status: 'SENT',
    timestamp: Date.now()
  });

  // 异步保存到数据库和更新会话（不阻塞消息发送）
  (async () => {
    try {
      // 保存消息到数据库
      logger.info(`准备保存消息到数据库 - messageId: ${messageId}, isGroupMessage: ${isGroupMessage}`);
      
      if (isGroupMessage) {
        // 群消息：为每个成员创建一条消息记录
        // 所有成员使用相同的messageId（通过receiver_id区分不同接收者）
        const [members] = await pool.query(
          'SELECT user_id FROM group_members WHERE group_id = ?',
          [conversationId]
        );
        
        for (const member of members) {
          // 所有成员使用相同的messageId，通过receiver_id区分
          try {
            await pool.query(
              `INSERT INTO messages 
               (message_id, conversation_id, sender_id, receiver_id, content, message_type, timestamp, status, extra)
               VALUES (?, ?, ?, ?, ?, ?, ?, 'SENT', ?)
               ON DUPLICATE KEY UPDATE
               status = 'SENT',
               timestamp = VALUES(timestamp)`,
              [messageId, conversationId, senderId, member.user_id, content, messageType, timestamp, extra || null]
            );
          } catch (error) {
            // 记录错误但不影响其他成员的消息保存
            if (error.code !== 'ER_DUP_ENTRY' && error.code !== 'ER_DATA_TOO_LONG') {
              logger.error(`保存群消息记录失败 - messageId: ${messageId}, receiverId: ${member.user_id}`, error);
            } else if (error.code === 'ER_DATA_TOO_LONG') {
              logger.error(`消息ID太长 - messageId: ${messageId}, receiverId: ${member.user_id}`, error);
            }
          }
        }
        
        logger.info(`✅ 群消息已保存到数据库 - messageId: ${messageId}, 保存了 ${members.length} 条记录`);
      } else {
        // 单聊消息：保存一条记录
        await pool.query(
          `INSERT INTO messages 
           (message_id, conversation_id, sender_id, receiver_id, content, message_type, timestamp, status, extra)
           VALUES (?, ?, ?, ?, ?, ?, ?, 'SENT', ?)`,
          [messageId, conversationId, senderId, receiverId, content, messageType, timestamp, extra || null]
        );
        logger.info(`✅ 单聊消息已保存到数据库 - messageId: ${messageId}`);
      }
      
      // 更新或创建会话
      await updateConversation(conversationId, senderId, receiverId || conversationId, content, timestamp, isGroupMessage);
    } catch (error) {
      logger.error(`❌ 保存消息到数据库失败 - messageId: ${messageId}`, error);
    }
  })();
}

// 处理撤回消息
async function handleRecallMessage(socket, data) {
  const { messageId } = data;
  const userId = socket.userId;

  const pool = getPool();
  // 查找消息
  const [messages] = await pool.query(
    'SELECT * FROM messages WHERE message_id = ?',
    [messageId]
  );

  if (messages.length === 0) {
    socket.emit('error', { message: '消息不存在' });
    return;
  }

  const message = messages[0];

  // 验证权限
  if (message.sender_id !== userId) {
    socket.emit('error', { message: '只能撤回自己的消息' });
    return;
  }

  // 检查时间限制
  const recallWindow = 2 * 60 * 1000;
  const messageAge = Date.now() - message.timestamp;
  if (messageAge > recallWindow) {
    socket.emit('error', { message: '消息发送超过2分钟，无法撤回' });
    return;
  }

  // 更新数据库
  await pool.query(
    'UPDATE messages SET is_recalled = ?, recall_by = ?, recall_time = ? WHERE message_id = ?',
    [true, userId, Date.now(), messageId]
  );

  // 通知发送者
  socket.emit('message_recalled', { messageId });

  // 通知接收者
  await sendToUser(message.receiver_id, 'message_recalled', {
    messageId,
    conversationId: message.conversation_id
  });
}

// 更新会话
async function updateConversation(conversationId, senderId, receiverId, lastMessage, timestamp, isGroupMessage = false) {
  const pool = getPool();
  
  if (isGroupMessage) {
    // 群聊：更新所有群成员的会话
    const [members] = await pool.query(
      'SELECT user_id FROM group_members WHERE group_id = ?',
      [conversationId]
    );
    
    // 获取群组信息
    const [groups] = await pool.query(
      'SELECT group_name, avatar FROM `groups` WHERE group_id = ?',
      [conversationId]
    );
    
    const group = groups[0];
    if (!group) {
      logger.error(`群组不存在 - groupId: ${conversationId}`);
      return;
    }
    
    // 获取发送者信息（用于显示在群消息中）
    const [senders] = await pool.query(
      'SELECT nickname FROM users WHERE user_id = ?',
      [senderId]
    );
    const sender = senders[0];
    
    // 为所有群成员更新会话
    for (const member of members) {
      const isSender = member.user_id === senderId;
      
      await pool.query(
        `INSERT INTO conversations 
         (conversation_id, type, target_id, target_name, target_avatar, last_message, last_message_time, unread_count)
         VALUES (?, 'GROUP', ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
         type = 'GROUP',
         target_id = ?,
         target_name = ?,
         target_avatar = ?,
         last_message = ?,
         last_message_time = ?,
         unread_count = ${isSender ? '0' : 'unread_count + 1'},
         updated_at = CURRENT_TIMESTAMP`,
        [
          conversationId,
          conversationId, // target_id = groupId
          group.group_name,
          group.avatar,
          lastMessage,
          timestamp,
          isSender ? 0 : 1, // 发送者未读数不增加
          conversationId, // UPDATE: target_id = groupId
          group.group_name, // UPDATE: target_name = groupName
          group.avatar, // UPDATE: target_avatar = groupAvatar
          lastMessage,
          timestamp
        ]
      );
    }
    
    logger.info(`✅ 群聊会话已更新 - groupId: ${conversationId}, 成员数: ${members.length}`);
  } else {
    // 单聊：更新发送者和接收者的会话
  // 获取发送者信息
  const [senders] = await pool.query(
    'SELECT nickname, avatar FROM users WHERE user_id = ?',
    [senderId]
  );

  const sender = senders[0];

  // 更新或创建会话（发送者视角）
  await pool.query(
    `INSERT INTO conversations 
     (conversation_id, type, target_id, target_name, target_avatar, last_message, last_message_time, unread_count)
     VALUES (?, 'SINGLE', ?, ?, ?, ?, ?, 0)
     ON DUPLICATE KEY UPDATE
     last_message = ?,
     last_message_time = ?,
     updated_at = CURRENT_TIMESTAMP`,
    [
      conversationId,
      receiverId,
      '', // target_name 需要从用户表获取
      null, // target_avatar
      lastMessage,
      timestamp,
      lastMessage,
      timestamp
    ]
  );

  // 更新或创建会话（接收者视角）
  await pool.query(
    `INSERT INTO conversations 
     (conversation_id, type, target_id, target_name, target_avatar, last_message, last_message_time, unread_count)
     VALUES (?, 'SINGLE', ?, ?, ?, ?, ?, unread_count + 1)
     ON DUPLICATE KEY UPDATE
     last_message = ?,
     last_message_time = ?,
     unread_count = unread_count + 1,
     updated_at = CURRENT_TIMESTAMP`,
    [
      conversationId,
      senderId,
      sender?.nickname || '',
      null,
      lastMessage,
      timestamp,
      lastMessage,
      timestamp
    ]
  );
  }
}

// 发送消息给指定用户
async function sendToUser(userId, event, data) {
  logger.info(`🚀 sendToUser被调用 - userId: ${userId}, event: ${event}, messageId: ${data?.messageId || 'N/A'}`);
  
  if (!ioInstance) {
    logger.error(`❌ 无法发送消息 - ioInstance未初始化, userId: ${userId}, event: ${event}`);
    return;
  }
  
  const sockets = userSockets.get(userId);
  logger.info(`📡 发送消息给用户 - userId: ${userId}, event: ${event}, 在线连接数: ${sockets?.size || 0}, userSockets Map大小: ${userSockets.size}`);
  
  // 如果内存中没有，尝试从Redis获取
  if (!sockets || sockets.size === 0) {
    try {
      const redisSockets = await getUserSockets(userId);
      if (redisSockets && redisSockets.length > 0) {
        logger.info(`从Redis获取到用户连接 - userId: ${userId}, socket数量: ${redisSockets.length}`);
        // 更新内存中的连接信息
        if (!userSockets.has(userId)) {
          userSockets.set(userId, new Set());
        }
        redisSockets.forEach(socketId => {
          userSockets.get(userId).add(socketId);
        });
      }
    } catch (error) {
      logger.warn(`从Redis获取用户连接失败 - userId: ${userId}`, error);
    }
  }
  
  const currentSockets = userSockets.get(userId);
  if (currentSockets && currentSockets.size > 0) {
    let sentCount = 0;
    let failedCount = 0;
    const invalidSocketIds = []; // 收集无效的socket ID，稍后统一清理
    
    currentSockets.forEach(socketId => {
      const socket = ioInstance.sockets.sockets.get(socketId);
      logger.debug(`检查socket - userId: ${userId}, socketId: ${socketId}, socket存在: ${!!socket}, connected: ${socket?.connected}`);
      
      if (socket && socket.connected) {
        try {
          logger.info(`📨 正在推送消息 - userId: ${userId}, socketId: ${socketId}, event: ${event}, messageId: ${data?.messageId || 'N/A'}`);
          socket.emit(event, data);
          sentCount++;
          logger.info(`✅ 消息推送成功 - userId: ${userId}, socketId: ${socketId}, event: ${event}, messageId: ${data?.messageId || 'N/A'}`);
        } catch (error) {
          failedCount++;
          logger.error(`❌ 发送消息失败 - userId: ${userId}, socketId: ${socketId}`, error);
          // 发送失败也标记为无效
          invalidSocketIds.push(socketId);
        }
      } else {
        failedCount++;
        logger.warn(`⚠️ Socket不存在或未连接 - userId: ${userId}, socketId: ${socketId}, socket存在: ${!!socket}, connected: ${socket?.connected}`);
        // 收集无效的socket ID（包括不存在的和未连接的）
        invalidSocketIds.push(socketId);
      }
    });
    
    // 清理所有无效的socket连接
    if (invalidSocketIds.length > 0) {
      logger.info(`清理无效socket连接 - userId: ${userId}, 数量: ${invalidSocketIds.length}`);
      invalidSocketIds.forEach(socketId => {
          currentSockets.delete(socketId);
        // 同时清理Redis中的连接记录
        removeSocketConnection(userId, socketId).catch(err => {
          logger.warn(`清理Redis中的socket连接失败 - userId: ${userId}, socketId: ${socketId}`, err);
        });
      });
      
      // 如果清理后该用户没有有效连接了，从Map中删除
      if (currentSockets.size === 0) {
        userSockets.delete(userId);
        logger.info(`用户 ${userId} 的所有连接都已失效，已从在线用户列表中移除`);
      }
    }
    
    logger.info(`消息发送完成 - userId: ${userId}, event: ${event}, 成功: ${sentCount}, 失败: ${failedCount}, 清理: ${invalidSocketIds.length}, 剩余有效连接: ${currentSockets.size}`);
    
    if (sentCount === 0) {
      logger.warn(`所有连接都失败 - userId: ${userId}, event: ${event}`);
    }
  } else {
    // 用户离线，消息已保存到数据库，可以通过推送服务通知
    logger.info(`用户 ${userId} 离线，无法发送实时通知 - event: ${event}, data: ${JSON.stringify(data).substring(0, 200)}`);
  }
}

// 广播消息（用于群聊）
function broadcastToRoom(roomId, event, data) {
  if (ioInstance) {
    ioInstance.to(roomId).emit(event, data);
  }
}

/**
 * 断开用户的所有WebSocket连接（用于单设备登录）
 */
async function disconnectUserSockets(userId, reason = '账号在其他设备登录') {
  if (!ioInstance) {
    logger.warn(`无法断开用户连接 - ioInstance未初始化, userId: ${userId}`);
    return;
  }
  
  logger.info(`开始断开用户所有连接 - userId: ${userId}, reason: ${reason}`);
  
  // 从内存中获取连接
  const sockets = userSockets.get(userId);
  let disconnectedCount = 0;
  
  if (sockets && sockets.size > 0) {
    sockets.forEach(socketId => {
      const socket = ioInstance.sockets.sockets.get(socketId);
      if (socket && socket.connected) {
        try {
          // 发送账号被踢通知
          socket.emit('account_kicked', {
            reason: reason,
            message: '您的账号在其他设备登录，当前设备已下线',
            timestamp: Date.now()
          });
          
          // 断开连接
          socket.disconnect(true);
          disconnectedCount++;
          logger.info(`已断开用户连接 - userId: ${userId}, socketId: ${socketId}, reason: ${reason}`);
        } catch (error) {
          logger.error(`断开用户连接失败 - userId: ${userId}, socketId: ${socketId}`, error);
        }
      }
    });
    
    // 清理内存中的连接记录
    userSockets.delete(userId);
  }
  
  // 从Redis中获取并断开
  try {
    const redisSockets = await getUserSockets(userId);
    if (redisSockets && redisSockets.length > 0) {
      redisSockets.forEach(socketId => {
        const socket = ioInstance.sockets.sockets.get(socketId);
        if (socket && socket.connected) {
          try {
            socket.emit('account_kicked', {
              reason: reason,
              message: '您的账号在其他设备登录，当前设备已下线',
              timestamp: Date.now()
            });
            socket.disconnect(true);
            disconnectedCount++;
          } catch (error) {
            logger.error(`断开Redis中的用户连接失败 - userId: ${userId}, socketId: ${socketId}`, error);
          }
        }
        // 清理Redis中的连接记录
        removeSocketConnection(userId, socketId).catch(err => {
          logger.warn(`清理Redis中的socket连接失败 - userId: ${userId}, socketId: ${socketId}`, err);
        });
      });
    }
  } catch (error) {
    logger.warn(`从Redis获取用户连接失败 - userId: ${userId}`, error);
  }
  
  // 设置用户离线
  try {
    await setUserOffline(userId);
  } catch (error) {
    logger.warn(`设置用户离线状态失败 - userId: ${userId}`, error);
  }
  
  logger.info(`用户连接断开完成 - userId: ${userId}, 断开连接数: ${disconnectedCount}, reason: ${reason}`);
}

// 处理视频通话请求
async function handleVideoCall(socket, data) {
  const { toUserId, fromUserId } = data;
  const senderId = socket.userId;

  if (!toUserId) {
    socket.emit('error', { message: '缺少接收方用户ID' });
    return;
  }

  logger.info(`视频通话请求 - from: ${senderId}, to: ${toUserId}`);

  // 发送给接收方
  sendToUser(toUserId, 'video_call_offer', {
    fromUserId: senderId,
    toUserId: toUserId,
    timestamp: Date.now()
  });
}

// 处理语音通话请求
async function handleVoiceCall(socket, data) {
  const { toUserId, fromUserId } = data;
  const senderId = socket.userId;

  if (!toUserId) {
    socket.emit('error', { message: '缺少接收方用户ID' });
    return;
  }

  logger.info(`语音通话请求 - from: ${senderId}, to: ${toUserId}`);

  // 发送给接收方
  sendToUser(toUserId, 'voice_call_offer', {
    fromUserId: senderId,
    toUserId: toUserId,
    timestamp: Date.now()
  });
}

// 处理视频通话 SDP
async function handleVideoCallSdp(socket, data) {
  const { toUserId, fromUserId, sdp, sdpType } = data;
  const senderId = socket.userId;

  if (!toUserId || !sdp || !sdpType) {
    socket.emit('error', { message: '缺少必需字段' });
    return;
  }

  logger.info(`视频通话SDP - from: ${senderId}, to: ${toUserId}, type: ${sdpType}`);

  // 转发 SDP 给接收方
  sendToUser(toUserId, 'video_call_sdp', {
    fromUserId: senderId,
    toUserId: toUserId,
    sdp: sdp,
    sdpType: sdpType,
    timestamp: Date.now()
  });
}

// 处理视频通话 ICE Candidate
async function handleVideoCallIce(socket, data) {
  const { toUserId, fromUserId, candidate, sdpMid, sdpMLineIndex } = data;
  const senderId = socket.userId;

  if (!toUserId || !candidate) {
    socket.emit('error', { message: '缺少必需字段' });
    return;
  }

  logger.info(`视频通话ICE Candidate - from: ${senderId}, to: ${toUserId}`);

  // 转发 ICE Candidate 给接收方
  sendToUser(toUserId, 'video_call_ice', {
    fromUserId: senderId,
    toUserId: toUserId,
    candidate: candidate,
    sdpMid: sdpMid,
    sdpMLineIndex: sdpMLineIndex,
    timestamp: Date.now()
  });
}

// 处理视频通话接听
async function handleVideoCallAnswer(socket, data) {
  const { toUserId, fromUserId } = data;
  const senderId = socket.userId;

  if (!toUserId) {
    socket.emit('error', { message: '缺少接收方用户ID' });
    return;
  }

  logger.info(`视频通话接听 - from: ${senderId}, to: ${toUserId}`);

  // 通知发起方
  sendToUser(toUserId, 'video_call_accepted', {
    fromUserId: senderId,
    toUserId: toUserId,
    timestamp: Date.now()
  });
}

// 处理视频通话拒绝
async function handleVideoCallReject(socket, data) {
  const { toUserId, fromUserId } = data;
  const senderId = socket.userId;

  if (!toUserId) {
    socket.emit('error', { message: '缺少接收方用户ID' });
    return;
  }

  logger.info(`视频通话拒绝 - from: ${senderId}, to: ${toUserId}`);

  // 通知发起方
  sendToUser(toUserId, 'video_call_rejected', {
    fromUserId: senderId,
    toUserId: toUserId,
    timestamp: Date.now()
  });
}

// 处理视频通话挂断
async function handleVideoCallHangup(socket, data) {
  const { toUserId, fromUserId } = data;
  const senderId = socket.userId;

  if (!toUserId) {
    socket.emit('error', { message: '缺少接收方用户ID' });
    return;
  }

  logger.info(`视频通话挂断 - from: ${senderId}, to: ${toUserId}`);

  // 通知对方
  sendToUser(toUserId, 'video_call_ended', {
    fromUserId: senderId,
    toUserId: toUserId,
    timestamp: Date.now()
  });
}

module.exports = {
  setupWebSocket,
  sendToUser,
  broadcastToRoom,
  disconnectUserSockets
};

