package com.tongxun.data.repository

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.tongxun.data.local.TongxunDatabase
import com.tongxun.data.local.entity.MessageEntity
import com.tongxun.data.local.entity.MessageStatus
import com.tongxun.data.model.MessageType
import com.tongxun.data.remote.WebSocketManager
import com.tongxun.data.remote.api.MessageApi
import com.tongxun.data.remote.api.ConversationApi
import com.tongxun.data.remote.dto.MessageDto
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.domain.repository.GroupRepository
import com.tongxun.domain.repository.MessageRepository
import com.tongxun.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.tongxun.data.local.dao.UserDao
import com.tongxun.data.remote.NetworkModule
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val database: TongxunDatabase,
    private val webSocketManager: WebSocketManager,
    private val messageApi: MessageApi,
    private val conversationApi: ConversationApi,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    @ApplicationContext private val context: Context
) : MessageRepository {
    
    private val TAG = "MessageRepositoryImpl"
    private val messageDao = database.messageDao()
    private val conversationDao = database.conversationDao()
    private val userDao = database.userDao()
    private val groupDao = database.groupDao()
    
    // 用于记录已删除的消息ID（持久化存储）
    private val deletedMessagesPrefs: SharedPreferences = context.getSharedPreferences("deleted_messages", Context.MODE_PRIVATE)
    
    private fun getDeletedMessageIds(): Set<String> {
        return try {
            deletedMessagesPrefs.getStringSet("message_ids", emptySet()) ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "获取已删除消息ID列表失败", e)
            emptySet()
        }
    }
    
    private fun addDeletedMessageId(messageId: String) {
        try {
            val currentSet = getDeletedMessageIds().toMutableSet()
            currentSet.add(messageId)
            deletedMessagesPrefs.edit().putStringSet("message_ids", currentSet).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存已删除消息ID失败 - messageId: $messageId", e)
        }
    }
    
    /**
     * 判断conversationId是否是群组ID
     */
    private suspend fun isGroupConversation(conversationId: String): Boolean {
        return try {
            // 先检查本地数据库
            val group = groupDao.getGroupById(conversationId)
            if (group != null) {
                return true
            }
            
            // 如果本地没有，检查conversationId的格式
            // 单聊的conversationId格式是 "userA_userB"（包含下划线）
            // 群聊的conversationId就是群组ID（UUID格式，不包含下划线）
            // 如果conversationId不包含下划线，且是UUID格式，很可能是群组
            if (!conversationId.contains("_") && conversationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE))) {
                // 尝试从服务器获取群组信息来确认
                try {
                    val groupResult = groupRepository.getGroupInfo(conversationId)
                    if (groupResult.isSuccess) {
                        val groupInfo = groupResult.getOrNull()
                        if (groupInfo != null) {
                            // 确实是群组，保存到本地数据库
                            val groupEntity = com.tongxun.data.local.entity.GroupEntity(
                                groupId = groupInfo.groupId,
                                groupName = groupInfo.groupName,
                                avatar = groupInfo.avatar,
                                description = groupInfo.description,
                                ownerId = groupInfo.ownerId,
                                memberCount = groupInfo.memberCount,
                                maxMemberCount = groupInfo.maxMemberCount,
                                createdAt = groupInfo.createdAt,
                                updatedAt = System.currentTimeMillis()
                            )
                            groupDao.insertGroup(groupEntity)
                            Log.d(TAG, "从服务器获取群组信息并保存到本地 - groupId=$conversationId, groupName=${groupInfo.groupName}")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "从服务器检查群组信息失败 - conversationId: $conversationId", e)
                }
            }
            
            false
        } catch (e: Exception) {
            Log.w(TAG, "检查群组ID失败 - conversationId: $conversationId", e)
            false
        }
    }
    
    override fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        android.util.Log.e(TAG, "🔥🔥🔥 getMessages() 被调用 - conversationId: ${conversationId.take(32)}...")
        val deletedMessageIds = getDeletedMessageIds()
        android.util.Log.e(TAG, "📋 已删除的消息ID数量: ${deletedMessageIds.size}")
        
        return messageDao.getMessagesFlow(conversationId)
            .map { messages ->
                android.util.Log.e(TAG, "🔥🔥🔥 getMessages() 收到消息列表 - conversationId: ${conversationId.take(32)}..., 消息数量: ${messages.size}")
                
                // 🔥 关键诊断：检查conversationId是否匹配
                val mismatchedMessages = messages.filter { it.conversationId != conversationId }
                if (mismatchedMessages.isNotEmpty()) {
                    android.util.Log.e(TAG, "❌❌❌ 发现conversationId不匹配的消息！查询ID: ${conversationId.take(32)}..., 不匹配数量: ${mismatchedMessages.size}")
                    mismatchedMessages.take(3).forEach { msg ->
                        android.util.Log.e(TAG, "  不匹配消息 - messageId: ${msg.messageId.take(8)}..., conversationId: ${msg.conversationId.take(32)}...")
                    }
                }
                
                // 详细记录每条消息
                if (messages.isNotEmpty()) {
                    android.util.Log.e(TAG, "📝 前5条消息详情:")
                    messages.take(5).forEachIndexed { index, message ->
                        android.util.Log.e(TAG, "  消息[$index] - messageId: ${message.messageId.take(8)}..., conversationId: ${message.conversationId.take(32)}..., senderId: ${message.senderId.take(8)}..., timestamp: ${message.timestamp}, content: ${message.content.take(30)}...")
                    }
                    if (messages.size > 5) {
                        android.util.Log.e(TAG, "  还有 ${messages.size - 5} 条消息...")
                    }
                } else {
                    android.util.Log.w(TAG, "⚠️⚠️⚠️ 数据库中没有找到消息！conversationId: ${conversationId.take(32)}...")
                    
                    // 🔥 诊断：检查数据库中是否有其他conversationId的消息
                    try {
                        val allMessages = messageDao.getAllMessages()
                        val conversationIdsInDb = allMessages.map { it.conversationId }.distinct()
                        android.util.Log.w(TAG, "📋 数据库中的所有conversationId: ${conversationIdsInDb.joinToString(", ") { it.take(16) + "..." }}")
                        
                        // 检查是否有相似或相关的conversationId
                        val similarIds = conversationIdsInDb.filter { 
                            it.contains(conversationId.take(8)) || conversationId.contains(it.take(8))
                        }
                        if (similarIds.isNotEmpty()) {
                            android.util.Log.w(TAG, "⚠️ 发现相似的conversationId: ${similarIds.joinToString(", ") { it.take(32) + "..." }}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "无法查询所有消息", e)
                    }
                }
                
                // 🔥 关键修复：过滤掉已删除的消息（虽然消息已经从数据库删除，但为了安全还是过滤一下）
                val filtered = messages.filter { message ->
                    val isDeleted = deletedMessageIds.contains(message.messageId)
                    if (isDeleted) {
                        Log.w(TAG, "🗑️ 从消息列表中过滤已删除的消息 - messageId=${message.messageId}, conversationId=$conversationId")
                    }
                    !isDeleted
                }
                if (filtered.size != messages.size) {
                    Log.e(TAG, "📋 消息列表过滤 - 原始: ${messages.size}, 过滤后: ${filtered.size}, 已删除: ${messages.size - filtered.size}")
                }
                
                android.util.Log.e(TAG, "✅✅✅ getMessages() 返回消息列表 - conversationId: ${conversationId.take(32)}..., 最终消息数量: ${filtered.size}")
                filtered
            }
    }
    
    override suspend fun loadMoreMessages(
        conversationId: String,
        beforeTimestamp: Long,
        limit: Int
    ): Result<List<MessageEntity>> {
        return try {
            val messages = messageDao.getMessagesBefore(conversationId, beforeTimestamp, limit)
            Result.success(messages.reversed()) // 反转顺序，使最旧的消息在前
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun hasMoreMessages(conversationId: String, beforeTimestamp: Long): Boolean {
        return try {
            val count = messageDao.getMessageCountBefore(conversationId, beforeTimestamp)
            count > 0
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun sendMessage(
        conversationId: String,
        receiverId: String,
        content: String,
        messageType: MessageType,
        extra: String?
    ): Result<MessageEntity> {
        Log.e(TAG, "🔥🔥🔥 MessageRepositoryImpl.sendMessage() 被调用")
        Log.e(TAG, "参数 - conversationId: $conversationId, receiverId: $receiverId, content: $content, messageType: $messageType")
        
        return try {
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            val currentUser = authRepository.getCurrentUser()
            if (currentUser == null) {
                Log.e(TAG, "❌ 用户未登录，无法发送消息")
                return Result.failure(Exception("用户未登录"))
            }
            val senderId = currentUser.userId
            Log.d(TAG, "当前用户 - userId: $senderId")
            
            // 🔥 关键修复：对于单聊，验证并修正conversationId，确保A和B的会话ID一致
            val finalConversationId = if (!isGroupConversation(conversationId)) {
                // 单聊：根据senderId和receiverId构建正确的conversationId
                val userIds = listOf(senderId, receiverId).sorted()
                val correctConversationId = "${userIds[0]}_${userIds[1]}"
                
                if (conversationId != correctConversationId) {
                    android.util.Log.e(TAG, "❌❌❌ 发送消息时conversationId不匹配！原始: $conversationId, 正确: $correctConversationId, senderId: ${senderId.take(8)}..., receiverId: ${receiverId.take(8)}...")
                }
                
                correctConversationId
            } else {
                // 群聊：使用原始的conversationId
                conversationId
            }
            
            android.util.Log.e(TAG, "🔥🔥🔥 发送消息 - messageId=${messageId.take(8)}..., 原始conversationId=${conversationId.take(16)}..., 最终conversationId=${finalConversationId.take(16)}..., senderId=${senderId.take(8)}..., receiverId=${receiverId.take(8)}...")
            
            // 🔥 关键修复：在插入消息之前，确保会话存在（外键约束要求）
            val conversationBeforeInsert = conversationDao.getConversation(finalConversationId)
            if (conversationBeforeInsert == null) {
                android.util.Log.e(TAG, "❌❌❌ 严重错误：发送消息时会话不存在，无法插入消息！conversationId: ${finalConversationId.take(32)}...")
                android.util.Log.e(TAG, "   尝试创建会话...")
                
                // 紧急创建会话
                val isGroup = isGroupConversation(finalConversationId)
                if (isGroup) {
                    val group = groupDao.getGroupById(finalConversationId)
                    val emergencyConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = finalConversationId,
                        type = com.tongxun.data.local.entity.ConversationType.GROUP,
                        targetId = finalConversationId,
                        targetName = group?.groupName ?: "群聊",
                        targetAvatar = group?.avatar,
                        lastMessage = content.take(50),
                        lastMessageTime = timestamp,
                        unreadCount = 0
                    )
                    conversationDao.insertConversation(emergencyConversation)
                    android.util.Log.e(TAG, "✅ 紧急创建了群聊会话 - conversationId: ${finalConversationId.take(32)}...")
                } else {
                    val userIds = listOf(senderId, receiverId).sorted()
                    val otherUserId = userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                    val otherUser = userRepository.getUserById(otherUserId)
                    val emergencyConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = finalConversationId,
                        type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                        targetId = otherUserId,
                        targetName = otherUser?.nickname ?: "用户",
                        targetAvatar = otherUser?.avatar,
                        lastMessage = content.take(50),
                        lastMessageTime = timestamp,
                        unreadCount = 0
                    )
                    conversationDao.insertConversation(emergencyConversation)
                    android.util.Log.e(TAG, "✅ 紧急创建了单聊会话 - conversationId: ${finalConversationId.take(32)}...")
                }
            }
            
            val message = MessageEntity(
                messageId = messageId,
                conversationId = finalConversationId, // 🔥 关键：使用正确的conversationId
                senderId = senderId,
                receiverId = receiverId,
                content = content,
                messageType = messageType,
                timestamp = timestamp,
                status = MessageStatus.SENDING,
                extra = extra
            )
            
            // 先保存到本地（这会触发Room Flow自动更新UI）
            try {
                messageDao.insertMessage(message)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌❌❌ 插入发送的消息失败 - messageId: ${messageId.take(8)}..., conversationId: ${finalConversationId.take(32)}...", e)
                
                // 再次检查会话是否存在
                val verifyConversation = conversationDao.getConversation(finalConversationId)
                if (verifyConversation == null) {
                    android.util.Log.e(TAG, "❌❌❌ 会话仍然不存在！这可能是外键约束失败的原因")
                } else {
                    android.util.Log.e(TAG, "✅ 会话存在，可能是其他原因导致插入失败")
                }
                
                throw e // 重新抛出异常
            }
            Log.e(TAG, "✅✅✅ 消息已保存到本地数据库 - messageId: $messageId, conversationId: $finalConversationId")
            Log.e(TAG, "   消息内容: $content, senderId: $senderId, receiverId: $receiverId")
            
            // 更新会话的最后一条消息和时间（发送消息时也需要更新会话）
            // 🔥 关键修复：使用正确的conversationId
            val existingConversation = conversationDao.getConversation(finalConversationId)
            if (existingConversation != null) {
                // 判断是否是群聊
                val isGroup = isGroupConversation(finalConversationId)
                
                var updatedConversation = existingConversation.copy(
                    lastMessage = formatMessagePreview(MessageDto(
                        messageId = messageId,
                        conversationId = finalConversationId, // 🔥 使用正确的conversationId
                        senderId = senderId,
                        receiverId = receiverId,
                        content = content,
                        messageType = messageType,
                        timestamp = timestamp,
                        extra = extra
                    )),
                    lastMessageTime = timestamp
                )
                
                // 如果是群聊，确保使用群组信息
                if (isGroup) {
                    // 确保会话类型是GROUP
                    if (updatedConversation.type != com.tongxun.data.local.entity.ConversationType.GROUP) {
                        updatedConversation = updatedConversation.copy(
                            type = com.tongxun.data.local.entity.ConversationType.GROUP,
                            targetId = finalConversationId // 群聊的targetId就是群组ID
                        )
                        Log.w(TAG, "修正发送消息的会话类型为群聊 - conversationId=$finalConversationId")
                    }
                    
                    // 确保使用群组名称和头像
                    val group = groupDao.getGroupById(finalConversationId)
                    if (group != null) {
                        // 如果群组信息存在，但会话的targetName不匹配，则更新
                        if (updatedConversation.targetName != group.groupName || updatedConversation.targetId != finalConversationId) {
                            updatedConversation = updatedConversation.copy(
                                targetId = finalConversationId, // 🔥 使用正确的conversationId
                                targetName = group.groupName,
                                targetAvatar = group.avatar
                            )
                            Log.d(TAG, "更新发送消息的会话群组信息 - conversationId=$finalConversationId, groupName=${group.groupName}")
                        }
                    } else {
                        // 如果本地没有群组信息，异步获取
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val groupResult = groupRepository.getGroupInfo(finalConversationId)
                                if (groupResult.isSuccess) {
                                    val groupInfo = groupResult.getOrNull()
                                    groupInfo?.let { g ->
                                        val furtherUpdated = updatedConversation.copy(
                                            targetId = finalConversationId, // 🔥 使用正确的conversationId
                                            targetName = g.groupName,
                                            targetAvatar = g.avatar
                                        )
                                        conversationDao.updateConversation(furtherUpdated)
                                        Log.d(TAG, "异步更新发送消息的会话群组信息 - conversationId=$finalConversationId, groupName=${g.groupName}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "异步获取群组信息失败 - groupId=$finalConversationId", e)
                            }
                        }
                    }
                }
                
                conversationDao.updateConversation(updatedConversation)
                Log.d(TAG, "发送消息的会话已更新 - conversationId=$finalConversationId")
            } else {
                // 会话不存在，创建新会话
                // 判断是群聊还是单聊
                val isGroup = isGroupConversation(finalConversationId)
                
                if (isGroup) {
                    // 群聊：获取群组信息
                    val group = groupDao.getGroupById(finalConversationId)
                    val newConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = finalConversationId, // 🔥 使用正确的conversationId
                        type = com.tongxun.data.local.entity.ConversationType.GROUP,
                        targetId = finalConversationId, // 群聊的targetId就是群组ID
                        targetName = group?.groupName ?: "群聊",
                        targetAvatar = group?.avatar,
                        lastMessage = formatMessagePreview(MessageDto(
                            messageId = messageId,
                            conversationId = finalConversationId, // 🔥 使用正确的conversationId
                            senderId = senderId,
                            receiverId = receiverId,
                            content = content,
                            messageType = messageType,
                            timestamp = timestamp,
                            extra = extra
                        )),
                        lastMessageTime = timestamp
                    )
                    conversationDao.insertConversation(newConversation)
                    Log.d(TAG, "发送群消息时创建了新会话 - conversationId=$finalConversationId, groupName=${group?.groupName}")
                } else {
                    // 单聊：获取用户信息
                    val targetUser = userRepository.getUserById(receiverId)
                    val newConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = finalConversationId, // 🔥 使用正确的conversationId
                        type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                        targetId = receiverId,
                        targetName = targetUser?.nickname ?: "",
                        targetAvatar = targetUser?.avatar,
                        lastMessage = formatMessagePreview(MessageDto(
                            messageId = messageId,
                            conversationId = finalConversationId, // 🔥 使用正确的conversationId
                            senderId = senderId,
                            receiverId = receiverId,
                            content = content,
                            messageType = messageType,
                            timestamp = timestamp,
                            extra = extra
                        )),
                        lastMessageTime = timestamp
                    )
                    conversationDao.insertConversation(newConversation)
                    Log.d(TAG, "发送单聊消息时创建了新会话 - conversationId=$finalConversationId")
                }
            }
            
            // 通过WebSocket发送
            // 对于群消息，receiverId应该为空或群组ID（服务器会根据conversationId判断）
            val isGroup = isGroupConversation(finalConversationId)
            val finalReceiverId = if (isGroup) {
                finalConversationId // 群消息的receiverId使用群组ID
            } else {
                receiverId // 单聊消息的receiverId使用接收者ID
            }
            
            val messageDto = MessageDto(
                messageId = messageId,
                conversationId = finalConversationId, // 🔥 使用正确的conversationId
                senderId = senderId,
                receiverId = finalReceiverId,
                content = content,
                messageType = messageType,
                timestamp = timestamp,
                extra = extra
            )
            
            Log.e(TAG, "🔥 准备通过WebSocket发送消息 - messageId: $messageId, conversationId: $finalConversationId, isGroup: $isGroup, finalReceiverId: $finalReceiverId")
            var sent = webSocketManager.sendMessage(messageDto)
            Log.e(TAG, "WebSocket发送结果 - sent: $sent, messageId: $messageId")
            
            // 如果发送失败且WebSocket未连接，尝试重新初始化并等待连接后重试
            if (!sent && !webSocketManager.isConnected()) {
                Log.e(TAG, "⚠️ WebSocket发送失败且未连接，尝试重新初始化并等待连接后重试")
                val token = authRepository.getToken()
                if (token != null) {
                    // 重新初始化WebSocket（确保token和URL正确）
                    val baseUrl = NetworkModule.BASE_URL.replace("/api/", "").trimEnd('/')
                    webSocketManager.initialize(baseUrl, token)
                    Log.e(TAG, "✅ WebSocket已重新初始化，等待连接建立后重试（最多等待2秒）")
                    
                    // 等待连接建立，最多等待2秒，每200ms检查一次
                    var connected = false
                    repeat(10) { // 10次 * 200ms = 2秒
                        kotlinx.coroutines.delay(200)
                        if (webSocketManager.isConnected()) {
                            connected = true
                            Log.e(TAG, "🔄 WebSocket已连接（等待了${(it + 1) * 200}ms），重试发送消息")
                            sent = webSocketManager.sendMessage(messageDto)
                            Log.e(TAG, "重试发送结果 - sent: $sent, messageId: $messageId")
                            return@repeat
                        }
                    }
                    
                    if (!connected) {
                        Log.e(TAG, "⚠️ WebSocket在2秒内仍未连接，无法重试发送")
                    }
                } else {
                    Log.e(TAG, "⚠️ Token为空，无法重新初始化WebSocket")
                }
            }
            
            if (sent) {
                messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
                Log.e(TAG, "✅ 消息状态已更新为SENT - messageId: $messageId")
            } else {
                messageDao.updateMessageStatus(messageId, MessageStatus.FAILED)
                Log.e(TAG, "❌ 消息状态已更新为FAILED - messageId: $messageId")
            }
            
            Result.success(message.copy(status = if (sent) MessageStatus.SENT else MessageStatus.FAILED))
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送消息异常", e)
            Result.failure(e)
        }
    }
    
    override suspend fun recallMessage(messageId: String) {
        try {
            // 先调用API撤回
            messageApi.recallMessage(messageId)
            // 更新本地数据库
            messageDao.recallMessage(messageId)
            // WebSocket通知由服务器端处理
        } catch (e: Exception) {
            throw e
        }
    }
    
    override suspend fun deleteMessage(messageId: String) {
        Log.e(TAG, "========== 🔥🔥🔥 开始删除消息 ==========")
        Log.e(TAG, "📝 删除消息 - messageId: $messageId")
        
        // 🔥 关键修复：先检查是否已经在已删除列表中
        val deletedMessageIds = getDeletedMessageIds()
        if (deletedMessageIds.contains(messageId)) {
            Log.w(TAG, "⚠️ 消息已在已删除列表中，跳过删除 - messageId: $messageId")
            // 即使已在列表中，也确保本地数据库中没有这条消息
            try {
                messageDao.deleteMessageById(messageId)
                Log.d(TAG, "✅ 已清理本地数据库中的消息 - messageId: $messageId")
            } catch (e: Exception) {
                Log.w(TAG, "清理本地消息失败 - messageId: $messageId", e)
            }
            return
        }
        
        // 先获取消息信息，用于日志
        val message = messageDao.getMessageById(messageId)
        if (message != null) {
            Log.e(TAG, "📝 消息信息 - conversationId: ${message.conversationId}, senderId: ${message.senderId}, receiverId: ${message.receiverId}, content: ${message.content.take(50)}")
        } else {
            Log.w(TAG, "⚠️ 本地未找到消息记录 - messageId: $messageId")
            // 即使本地没有，也记录到已删除列表，防止服务器端还有记录
            addDeletedMessageId(messageId)
            Log.e(TAG, "📝 本地无记录，但已记录到已删除消息列表 - messageId: $messageId")
            return
        }
        
        // 🔥 关键修复：先记录到已删除列表（防止在删除过程中消息被重新插入）
        addDeletedMessageId(messageId)
        Log.e(TAG, "📝 已记录到已删除消息列表（删除前）- messageId: $messageId")
        
        // 先删除服务器端的记录
        try {
            Log.e(TAG, "🌐 开始删除服务器端消息 - messageId: $messageId")
            messageApi.deleteMessage(messageId)
            Log.e(TAG, "✅ 服务器端消息删除成功 - messageId: $messageId")
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) {
                Log.w(TAG, "⚠️ 服务器端消息不存在（404），继续删除本地记录 - messageId: $messageId")
            } else {
                Log.e(TAG, "❌ 删除服务器端消息失败 - messageId: $messageId, HTTP ${e.code()}", e)
                // 即使服务器删除失败，也继续删除本地记录
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除服务器端消息异常 - messageId: $messageId", e)
            // 即使服务器删除失败，也继续删除本地记录
        }
        
        // 删除本地记录
        try {
            Log.e(TAG, "💾 开始删除本地消息 - messageId: $messageId")
            messageDao.deleteMessageById(messageId)
            
            // 验证删除是否成功
            val verifyMessage = messageDao.getMessageById(messageId)
            if (verifyMessage == null) {
                Log.e(TAG, "✅ 本地消息删除成功 - messageId: $messageId")
            } else {
                Log.w(TAG, "⚠️ 本地消息删除后仍存在 - messageId: $messageId")
            }
            
            // 验证已删除列表
            val verifyDeleted = getDeletedMessageIds()
            if (verifyDeleted.contains(messageId)) {
                Log.e(TAG, "✅ 已删除消息列表验证成功 - messageId: $messageId, 列表大小: ${verifyDeleted.size}")
            } else {
                Log.e(TAG, "❌ 已删除消息列表验证失败 - messageId: $messageId 不在列表中")
                // 重新添加
                addDeletedMessageId(messageId)
                Log.e(TAG, "📝 已重新添加到已删除消息列表 - messageId: $messageId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除本地消息异常 - messageId: $messageId", e)
            throw e // 重新抛出异常，让调用者知道删除失败
        }
        
        Log.e(TAG, "========== 🔥🔥🔥 删除消息完成 ==========")
    }
    
    override suspend fun markAsRead(conversationId: String) {
        try {
            // 调用API标记已读
            messageApi.markAsRead(
                com.tongxun.data.repository.MarkAsReadRequest(
                    conversationId = conversationId,
                    messageIds = null // null表示标记会话中所有消息为已读
                )
            )
            // 更新本地数据库
            conversationDao.clearUnreadCount(conversationId)
        } catch (e: Exception) {
            // 即使API调用失败，也清除本地未读数
            conversationDao.clearUnreadCount(conversationId)
        }
    }

    /**
     * 获取最后一条消息的时间戳（用于拉取离线消息）
     */
    suspend fun getLastMessageTimestamp(): Long? {
        return messageDao.getLastMessage()?.timestamp
    }
    
    /**
     * 格式化消息预览文本（用于会话列表显示）
     */
    private fun formatMessagePreview(messageDto: MessageDto): String {
        return when (messageDto.messageType) {
            MessageType.IMAGE -> "[图片]"
            MessageType.VOICE -> "[语音]"
            MessageType.FILE -> "[文件]"
            MessageType.VIDEO -> "[视频]"
            MessageType.RED_PACKET -> "[红包]"
            MessageType.SYSTEM -> "[系统消息]"
            else -> messageDto.content
        }
    }
    
    // 处理接收到的消息
    suspend fun handleReceivedMessage(messageDto: MessageDto) {
        Log.e(TAG, "🔥🔥🔥 开始处理接收到的消息 - messageId=${messageDto.messageId}, conversationId=${messageDto.conversationId}, senderId=${messageDto.senderId}, receiverId=${messageDto.receiverId}, content=${messageDto.content.take(50)}")
        
        // 🔥 关键修复：检查消息是否在已删除列表中
        val deletedMessageIds = getDeletedMessageIds()
        if (deletedMessageIds.contains(messageDto.messageId)) {
            Log.e(TAG, "🗑️ 收到已删除的消息，忽略处理 - messageId=${messageDto.messageId}, content=${messageDto.content.take(30)}")
            return
        }
        
        val currentUser = authRepository.getCurrentUser()
        if (currentUser == null) {
            Log.w(TAG, "用户未登录，无法处理消息")
            return
        }
        
        // 🔥 关键：先判断消息类型（群组还是个人），使用conversationId判断
        var isGroup = isGroupConversation(messageDto.conversationId)
        
        // 🔥 关键修复：如果判断为单聊，但conversationId是UUID格式（不包含下划线），可能是群聊
        // 这种情况下，再次尝试从服务器获取群组信息，避免误判
        if (!isGroup) {
            val conversationId = messageDto.conversationId
            // 如果conversationId不包含下划线且是UUID格式，很可能是群组
            if (!conversationId.contains("_") && conversationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE))) {
                Log.w(TAG, "⚠️⚠️⚠️ conversationId是UUID格式但判断为单聊，尝试从服务器确认是否为群组 - conversationId=$conversationId")
                try {
                    val groupResult = groupRepository.getGroupInfo(conversationId)
                    if (groupResult.isSuccess) {
                        val groupInfo = groupResult.getOrNull()
                        if (groupInfo != null) {
                            // 确实是群组，保存到本地数据库
                            val groupEntity = com.tongxun.data.local.entity.GroupEntity(
                                groupId = groupInfo.groupId,
                                groupName = groupInfo.groupName,
                                avatar = groupInfo.avatar,
                                description = groupInfo.description,
                                ownerId = groupInfo.ownerId,
                                memberCount = groupInfo.memberCount,
                                maxMemberCount = groupInfo.maxMemberCount,
                                createdAt = groupInfo.createdAt,
                                updatedAt = System.currentTimeMillis()
                            )
                            groupDao.insertGroup(groupEntity)
                            isGroup = true
                            Log.e(TAG, "✅✅✅ 从服务器确认是群组，已保存到本地 - groupId=$conversationId, groupName=${groupInfo.groupName}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "从服务器检查群组信息失败 - conversationId: $conversationId", e)
                }
            }
        }
        
        Log.e(TAG, "🔥🔥🔥 消息类型判断 - conversationId=${messageDto.conversationId}, isGroup=$isGroup")
        
        // 检查是否是自己发送的消息（通过WebSocket收到的确认）
        if (messageDto.senderId == currentUser.userId) {
            Log.d(TAG, "收到自己发送的消息确认 - messageId=${messageDto.messageId}")
            // 更新发送状态
            messageDao.updateMessageStatus(messageDto.messageId, MessageStatus.SENT)
            Log.d(TAG, "消息状态已更新为SENT - messageId=${messageDto.messageId}")
            
            // 更新会话的最后一条消息（发送消息时也需要更新会话）
            val conversationId = messageDto.conversationId
            val existingConversation = conversationDao.getConversation(conversationId)
            if (existingConversation != null) {
                // 判断是否是群聊
                val isGroup = isGroupConversation(conversationId)
                
                var updatedConversation = existingConversation.copy(
                    lastMessage = formatMessagePreview(messageDto),
                    lastMessageTime = messageDto.timestamp
                )
                
                // 如果是群聊，确保使用群组信息
                if (isGroup) {
                    // 确保会话类型是GROUP
                    if (updatedConversation.type != com.tongxun.data.local.entity.ConversationType.GROUP) {
                        updatedConversation = updatedConversation.copy(
                            type = com.tongxun.data.local.entity.ConversationType.GROUP,
                            targetId = conversationId // 群聊的targetId就是群组ID
                        )
                        Log.w(TAG, "修正消息确认的会话类型为群聊 - conversationId=$conversationId")
                    }
                    
                    // 确保使用群组名称和头像
                    val group = groupDao.getGroupById(conversationId)
                    if (group != null) {
                        // 如果群组信息存在，但会话的targetName不匹配，则更新
                        if (updatedConversation.targetName != group.groupName || updatedConversation.targetId != conversationId) {
                            updatedConversation = updatedConversation.copy(
                                targetId = conversationId,
                                targetName = group.groupName,
                                targetAvatar = group.avatar
                            )
                            Log.d(TAG, "更新消息确认的会话群组信息 - conversationId=$conversationId, groupName=${group.groupName}")
                        }
                    } else {
                        // 如果本地没有群组信息，异步获取
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val groupResult = groupRepository.getGroupInfo(conversationId)
                                if (groupResult.isSuccess) {
                                    val groupInfo = groupResult.getOrNull()
                                    groupInfo?.let { g ->
                                        val furtherUpdated = updatedConversation.copy(
                                            targetId = conversationId,
                                            targetName = g.groupName,
                                            targetAvatar = g.avatar
                                        )
                                        conversationDao.updateConversation(furtherUpdated)
                                        Log.d(TAG, "异步更新消息确认的会话群组信息 - conversationId=$conversationId, groupName=${g.groupName}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "异步获取群组信息失败 - groupId=$conversationId", e)
                            }
                        }
                    }
                }
                
                conversationDao.updateConversation(updatedConversation)
                Log.d(TAG, "发送消息的会话已更新 - conversationId=$conversationId")
            }
        } else {
            Log.d(TAG, "收到他人发送的消息 - messageId=${messageDto.messageId}, senderId=${messageDto.senderId}, content=${messageDto.content.take(50)}")
            
            // 检查消息是否已存在，防止重复处理
            val existingMessage = messageDao.getMessageById(messageDto.messageId)
            if (existingMessage != null) {
                Log.w(TAG, "消息已存在，跳过处理 - messageId=${messageDto.messageId}")
                return
            }
            
            // 先确保会话存在，如果不存在则创建（必须在插入消息之前）
            val conversationId = messageDto.conversationId
            Log.e(TAG, "🔥🔥🔥 准备创建/更新会话 - conversationId=$conversationId, isGroup=$isGroup")
            val existingConversation = conversationDao.getConversation(conversationId)
            if (existingConversation == null) {
                // 会话不存在，创建会话
                // 使用之前判断的isGroup结果（避免重复判断）
                Log.e(TAG, "🔥🔥🔥 会话不存在，需要创建新会话 - conversationId=$conversationId, isGroup=$isGroup")
                
                if (isGroup) {
                    // 群聊：获取群组信息（isGroupConversation已经确保本地数据库有群组信息）
                    val group = groupDao.getGroupById(conversationId)
                    val newConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = conversationId,
                        type = com.tongxun.data.local.entity.ConversationType.GROUP,
                        targetId = conversationId, // 群聊的targetId就是群组ID，不是发送者ID
                        targetName = group?.groupName ?: "群聊", // 群组名称，不是发送者名称
                        targetAvatar = group?.avatar, // 群组头像，不是发送者头像
                        lastMessage = formatMessagePreview(messageDto),
                        lastMessageTime = messageDto.timestamp,
                        unreadCount = 1
                    )
                    conversationDao.insertConversation(newConversation)
                    Log.e(TAG, "✅✅✅ 接收群消息时创建了新会话 - conversationId=$conversationId, groupName=${group?.groupName}, targetId=$conversationId")
                    
                    // 如果本地没有群组信息，异步获取并更新会话（不阻塞消息接收）
                    if (group == null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                Log.w(TAG, "本地没有群组信息，从服务器获取 - groupId=$conversationId")
                                val groupResult = groupRepository.getGroupInfo(conversationId)
                                if (groupResult.isSuccess) {
                                    val groupInfo = groupResult.getOrNull()
                                    groupInfo?.let { g ->
                                        // 同时更新本地群组数据库
                                        val groupEntity = com.tongxun.data.local.entity.GroupEntity(
                                            groupId = g.groupId,
                                            groupName = g.groupName,
                                            avatar = g.avatar,
                                            description = g.description,
                                            ownerId = g.ownerId,
                                            memberCount = g.memberCount,
                                            maxMemberCount = g.maxMemberCount,
                                            createdAt = g.createdAt,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        groupDao.insertGroup(groupEntity)
                                        
                                        val updatedConversation = newConversation.copy(
                                            targetId = conversationId,
                                            targetName = g.groupName,
                                            targetAvatar = g.avatar
                                        )
                                        conversationDao.updateConversation(updatedConversation)
                                        Log.e(TAG, "✅✅✅ 会话群组信息已更新（创建时异步获取） - conversationId=$conversationId, groupName=${g.groupName}")
                                    }
                                } else {
                                    Log.w(TAG, "获取群组信息失败 - groupId=$conversationId")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "获取群组信息失败 - groupId=$conversationId", e)
                            }
                        }
                    }
                } else {
                    // 单聊：conversationId的格式应该是 "userA_userB"（排序后的两个用户ID）
                    // 🔥 关键修复：如果conversationId是UUID格式（不包含下划线），说明conversationId是错误的
                    // 需要根据senderId和receiverId重新构建正确的conversationId
                    var correctedConversationId = conversationId
                    var otherUserId = messageDto.senderId // 默认使用senderId
                    
                    if (!conversationId.contains("_")) {
                        // conversationId不包含下划线，说明格式不正确
                        // 根据senderId和receiverId构建正确的conversationId
                        val userIds = listOf(messageDto.senderId, messageDto.receiverId).sorted()
                        correctedConversationId = "${userIds[0]}_${userIds[1]}"
                        otherUserId = userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                        Log.w(TAG, "⚠️⚠️⚠️ 修正单聊conversationId - 原始: $conversationId, 修正后: $correctedConversationId, senderId: ${messageDto.senderId}, receiverId: ${messageDto.receiverId}")
                        
                        // 如果原始conversationId存在错误的会话记录，删除它
                        try {
                            val wrongConversation = conversationDao.getConversation(conversationId)
                            if (wrongConversation != null && wrongConversation.type == com.tongxun.data.local.entity.ConversationType.SINGLE) {
                                conversationDao.deleteConversationById(conversationId)
                                Log.w(TAG, "🗑️ 已删除错误的单聊会话记录 - conversationId: $conversationId")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "删除错误会话记录失败 - conversationId: $conversationId", e)
                        }
                        
                        // 更新messageDto的conversationId
                        val updatedMessageDto = messageDto.copy(conversationId = correctedConversationId)
                        // 使用修正后的conversationId继续处理
                        return handleReceivedMessage(updatedMessageDto)
                    } else {
                        // conversationId格式正确（包含下划线），但需要验证内容是否正确
                        val userIds = conversationId.split("_")
                        if (userIds.size == 2) {
                            // 验证conversationId是否与senderId和receiverId匹配
                            val expectedUserIds = listOf(messageDto.senderId, messageDto.receiverId).sorted()
                            val expectedConversationId = "${expectedUserIds[0]}_${expectedUserIds[1]}"
                            
                            if (conversationId != expectedConversationId) {
                                // conversationId格式正确但内容不匹配，需要修正
                                android.util.Log.e(TAG, "❌❌❌ 单聊conversationId内容不匹配！原始: $conversationId, 正确: $expectedConversationId, senderId: ${messageDto.senderId.take(8)}..., receiverId: ${messageDto.receiverId.take(8)}...")
                                correctedConversationId = expectedConversationId
                                
                                // 删除错误的会话记录
                                try {
                                    val wrongConversation = conversationDao.getConversation(conversationId)
                                    if (wrongConversation != null && wrongConversation.type == com.tongxun.data.local.entity.ConversationType.SINGLE) {
                                        conversationDao.deleteConversationById(conversationId)
                                        android.util.Log.e(TAG, "🗑️ 已删除错误的单聊会话记录 - conversationId: $conversationId")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w(TAG, "删除错误会话记录失败 - conversationId: $conversationId", e)
                                }
                                
                                // 更新messageDto的conversationId并递归调用
                                val updatedMessageDto = messageDto.copy(conversationId = correctedConversationId)
                                return handleReceivedMessage(updatedMessageDto)
                            }
                            
                            // conversationId正确，从conversationId中提取对方ID
                            otherUserId = userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                        } else {
                            // 如果格式不正确（下划线数量不对），使用senderId作为targetId，并修正conversationId
                            android.util.Log.e(TAG, "❌❌❌ 单聊conversationId格式不正确（下划线数量不对）！conversationId: $conversationId")
                            val expectedUserIds = listOf(messageDto.senderId, messageDto.receiverId).sorted()
                            correctedConversationId = "${expectedUserIds[0]}_${expectedUserIds[1]}"
                            otherUserId = expectedUserIds.firstOrNull { it != currentUser.userId } ?: expectedUserIds.first()
                            
                            // 更新messageDto的conversationId并递归调用
                            val updatedMessageDto = messageDto.copy(conversationId = correctedConversationId)
                            return handleReceivedMessage(updatedMessageDto)
                        }
                    }
                    
                    // 先尝试从本地获取用户信息（快速路径，不阻塞）
                    val otherUser = userDao.getUserById(otherUserId)
                    
                    // 🔥 关键修复：使用正确的conversationId（如果被修正了，使用修正后的）
                    val finalConversationId = correctedConversationId // 始终使用correctedConversationId（如果没修正，它就是原始值）
                    
                    // 如果本地没有，先使用默认值创建会话，然后异步获取用户信息
                    val newConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = finalConversationId,
                        type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                        targetId = otherUserId,
                        targetName = otherUser?.nickname ?: "用户",
                        targetAvatar = otherUser?.avatar,
                        lastMessage = formatMessagePreview(messageDto),
                        lastMessageTime = messageDto.timestamp,
                        unreadCount = 1
                    )
                    conversationDao.insertConversation(newConversation)
                    Log.e(TAG, "✅✅✅ 接收单聊消息时创建了新会话 - conversationId=$finalConversationId, targetId=$otherUserId, targetName=${newConversation.targetName}")
                    
                    // 如果本地没有用户信息，异步获取并更新会话（不阻塞消息接收）
                    if (otherUser == null) {
                        // 使用IO调度器异步获取用户信息，不阻塞当前消息处理
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val remoteUser = userRepository.getUserById(otherUserId)
                                remoteUser?.let { user ->
                                    val updatedConversation = newConversation.copy(
                                        targetName = user.nickname,
                                        targetAvatar = user.avatar
                                    )
                                    conversationDao.updateConversation(updatedConversation)
                                    Log.d(TAG, "会话用户信息已更新 - conversationId=$conversationId")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "获取用户信息失败，使用默认值 - userId=$otherUserId", e)
                            }
                        }
                    }
                }
            }
            
            // 现在会话已确保存在，可以安全地插入消息
            // 🔥 关键修复：确保使用正确的conversationId，必须与会话创建时使用的conversationId一致
            // 对于单聊，如果conversationId被修正过，需要重新查询会话
            val finalConversationIdForMessage = if (!isGroup) {
                // 单聊：根据senderId和receiverId计算正确的conversationId
                val userIds = listOf(messageDto.senderId, messageDto.receiverId).sorted()
                val correctConversationId = "${userIds[0]}_${userIds[1]}"
                
                // 🔥 关键：重新查询会话，因为conversationId可能已经被修正过
                val actualConversation = conversationDao.getConversation(correctConversationId)
                if (actualConversation != null) {
                    // 使用已存在会话的conversationId（确保外键约束通过）
                    actualConversation.conversationId
                } else {
                    // 会话不存在，使用计算出的conversationId（这种情况理论上不应该发生，因为前面已经创建了会话）
                    android.util.Log.e(TAG, "❌❌❌ 严重警告：会话不存在！conversationId: ${correctConversationId.take(32)}...")
                    correctConversationId
                }
            } else {
                // 群聊：使用原始的conversationId（确保与会话创建时一致）
                val actualConversation = conversationDao.getConversation(messageDto.conversationId)
                if (actualConversation != null) {
                    actualConversation.conversationId
                } else {
                    android.util.Log.e(TAG, "❌❌❌ 严重警告：群聊会话不存在！conversationId: ${messageDto.conversationId.take(32)}...")
                    messageDto.conversationId
                }
            }
            
            android.util.Log.e(TAG, "🔥🔥🔥 最终使用的conversationId - 原始: ${messageDto.conversationId.take(16)}..., 最终: ${finalConversationIdForMessage.take(16)}..., isGroup: $isGroup")
            
            android.util.Log.e(TAG, "🔥🔥🔥 保存消息 - messageId=${messageDto.messageId.take(8)}..., 原始conversationId=${messageDto.conversationId.take(16)}..., 最终conversationId=${finalConversationIdForMessage.take(16)}..., senderId=${messageDto.senderId.take(8)}..., receiverId=${messageDto.receiverId.take(8)}..., isGroup=$isGroup")
            
            val message = MessageEntity(
                messageId = messageDto.messageId,
                conversationId = finalConversationIdForMessage, // 🔥 关键：使用正确的conversationId
                senderId = messageDto.senderId,
                receiverId = messageDto.receiverId,
                content = messageDto.content,
                messageType = messageDto.messageType,
                timestamp = messageDto.timestamp,
                status = MessageStatus.SENT,
                extra = messageDto.extra
            )
            
            // 🔥 关键修复：在插入消息之前，确保会话存在（外键约束要求）
            val conversationBeforeInsert = conversationDao.getConversation(finalConversationIdForMessage)
            if (conversationBeforeInsert == null) {
                android.util.Log.e(TAG, "❌❌❌ 严重错误：会话不存在，无法插入消息！conversationId: ${finalConversationIdForMessage.take(32)}..., isGroup: $isGroup")
                android.util.Log.e(TAG, "   尝试创建会话...")
                
                // 紧急创建会话
                if (isGroup) {
                    val group = groupDao.getGroupById(finalConversationIdForMessage)
                    val emergencyConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = finalConversationIdForMessage,
                        type = com.tongxun.data.local.entity.ConversationType.GROUP,
                        targetId = finalConversationIdForMessage,
                        targetName = group?.groupName ?: "群聊",
                        targetAvatar = group?.avatar,
                        lastMessage = formatMessagePreview(messageDto),
                        lastMessageTime = messageDto.timestamp,
                        unreadCount = 0
                    )
                    conversationDao.insertConversation(emergencyConversation)
                    android.util.Log.e(TAG, "✅ 紧急创建了群聊会话 - conversationId: ${finalConversationIdForMessage.take(32)}...")
                } else {
                    val userIds = listOf(messageDto.senderId, messageDto.receiverId).sorted()
                    val otherUserId = userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                    val otherUser = userDao.getUserById(otherUserId)
                    val emergencyConversation = com.tongxun.data.local.entity.ConversationEntity(
                        conversationId = finalConversationIdForMessage,
                        type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                        targetId = otherUserId,
                        targetName = otherUser?.nickname ?: "用户",
                        targetAvatar = otherUser?.avatar,
                        lastMessage = formatMessagePreview(messageDto),
                        lastMessageTime = messageDto.timestamp,
                        unreadCount = 0
                    )
                    conversationDao.insertConversation(emergencyConversation)
                    android.util.Log.e(TAG, "✅ 紧急创建了单聊会话 - conversationId: ${finalConversationIdForMessage.take(32)}...")
                }
            }
            
            try {
                messageDao.insertMessage(message)
                Log.e(TAG, "✅✅✅ 接收消息已保存到本地数据库 - messageId=${messageDto.messageId}, conversationId=$finalConversationIdForMessage, isGroup=$isGroup")
                Log.e(TAG, "   消息内容: ${messageDto.content.take(50)}..., senderId=${messageDto.senderId.take(8)}..., receiverId=${messageDto.receiverId.take(8)}...")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌❌❌ 插入消息失败 - messageId: ${messageDto.messageId.take(8)}..., conversationId: ${finalConversationIdForMessage.take(32)}...", e)
                
                // 再次检查会话是否存在
                val verifyConversation = conversationDao.getConversation(finalConversationIdForMessage)
                if (verifyConversation == null) {
                    android.util.Log.e(TAG, "❌❌❌ 会话仍然不存在！这可能是外键约束失败的原因")
                } else {
                    android.util.Log.e(TAG, "✅ 会话存在，可能是其他原因导致插入失败")
                }
                
                throw e // 重新抛出异常
            }
            
            // 更新会话：未读数+1，更新最后一条消息和时间
            // 🔥 关键修复：使用正确的conversationId
            val finalConversationIdForUpdate = finalConversationIdForMessage
            val finalConversation = existingConversation ?: conversationDao.getConversation(finalConversationIdForUpdate)
            if (finalConversation != null) {
                conversationDao.increaseUnreadCount(finalConversationIdForUpdate, 1)
                
                // 🔥 关键修复：先判断是否是群聊（这个函数会从服务器获取群组信息如果本地没有）
                val isGroupForUpdate = isGroupConversation(finalConversationIdForUpdate)
                
                Log.e(TAG, "🔥🔥🔥 更新已存在的会话 - conversationId=$finalConversationIdForUpdate, isGroup=$isGroupForUpdate, currentType=${finalConversation.type}, currentTargetId=${finalConversation.targetId}, currentTargetName=${finalConversation.targetName}")
                
                // 更新最后一条消息和时间
                var updatedConversation = finalConversation.copy(
                    lastMessage = formatMessagePreview(messageDto),
                    lastMessageTime = messageDto.timestamp
                )
                
                // 🔥 关键修复：如果是群聊，强制使用群组信息，而不是发送者信息
                if (isGroupForUpdate) {
                    // 确保会话类型是GROUP
                    if (updatedConversation.type != com.tongxun.data.local.entity.ConversationType.GROUP) {
                        updatedConversation = updatedConversation.copy(
                            type = com.tongxun.data.local.entity.ConversationType.GROUP,
                            targetId = finalConversationIdForUpdate // 群聊的targetId就是群组ID
                        )
                        Log.w(TAG, "⚠️⚠️⚠️ 修正会话类型为群聊 - conversationId=$finalConversationIdForUpdate, 原来类型=${finalConversation.type}")
                    }
                    
                    // 强制使用群组名称和头像（从本地数据库获取，isGroupConversation已经确保本地有数据）
                    val group = groupDao.getGroupById(finalConversationIdForUpdate)
                    if (group != null) {
                        // 🔥 强制更新群组信息，无论之前是什么，都更新为群组信息
                        updatedConversation = updatedConversation.copy(
                            type = com.tongxun.data.local.entity.ConversationType.GROUP,
                            targetId = finalConversationIdForUpdate, // 群聊的targetId就是群组ID，不是发送者ID
                            targetName = group.groupName, // 群组名称，不是发送者名称
                            targetAvatar = group.avatar // 群组头像，不是发送者头像
                        )
                        Log.e(TAG, "✅✅✅ 接收消息：强制更新会话群组信息 - conversationId=$finalConversationIdForUpdate, groupName=${group.groupName}, targetId=${finalConversationIdForUpdate}, 原来targetName=${finalConversation.targetName}")
                    } else {
                        // 如果本地没有群组信息（理论上不应该发生，因为isGroupConversation已经处理了），异步获取
                        Log.w(TAG, "⚠️⚠️⚠️ 本地没有群组信息，异步获取 - conversationId=$finalConversationIdForUpdate")
                        updatedConversation = updatedConversation.copy(
                            type = com.tongxun.data.local.entity.ConversationType.GROUP,
                            targetId = finalConversationIdForUpdate
                        )
                        
                        // 异步获取群组信息
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val groupResult = groupRepository.getGroupInfo(finalConversationIdForUpdate)
                                if (groupResult.isSuccess) {
                                    val groupInfo = groupResult.getOrNull()
                                    groupInfo?.let { g ->
                                        // 同时更新本地群组数据库
                                        val groupEntity = com.tongxun.data.local.entity.GroupEntity(
                                            groupId = g.groupId,
                                            groupName = g.groupName,
                                            avatar = g.avatar,
                                            description = g.description,
                                            ownerId = g.ownerId,
                                            memberCount = g.memberCount,
                                            maxMemberCount = g.maxMemberCount,
                                            createdAt = g.createdAt,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        groupDao.insertGroup(groupEntity)
                                        
                                        val furtherUpdated = updatedConversation.copy(
                                            targetId = finalConversationIdForUpdate,
                                            targetName = g.groupName,
                                            targetAvatar = g.avatar
                                        )
                                        conversationDao.updateConversation(furtherUpdated)
                                        Log.e(TAG, "✅✅✅ 异步更新接收消息的会话群组信息 - conversationId=$finalConversationIdForUpdate, groupName=${g.groupName}")
                                    }
                                } else {
                                    Log.w(TAG, "获取群组信息失败 - groupId=$conversationId")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "异步获取群组信息失败 - groupId=$conversationId", e)
                            }
                        }
                    }
                } else {
                    // 单聊：确保使用对方信息，而不是发送者信息
                    // 🔥 关键修复：先检查targetName是否是发送者的姓名，如果是，可能是误判为单聊的群聊
                    val senderUser = userDao.getUserById(messageDto.senderId)
                    val isTargetNameSenderName = senderUser != null && updatedConversation.targetName == senderUser.nickname
                    
                    // 如果targetName是发送者的姓名，且conversationId是UUID格式，可能是群聊
                    if (isTargetNameSenderName && !conversationId.contains("_") && conversationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE))) {
                        Log.w(TAG, "⚠️⚠️⚠️ 发现targetName是发送者姓名且conversationId是UUID，可能是误判的群聊，重新检查 - conversationId=$conversationId, targetName=${updatedConversation.targetName}")
                        try {
                            val groupResult = groupRepository.getGroupInfo(conversationId)
                            if (groupResult.isSuccess) {
                                val groupInfo = groupResult.getOrNull()
                                if (groupInfo != null) {
                                    // 确实是群组，修正会话信息
                                    val groupEntity = com.tongxun.data.local.entity.GroupEntity(
                                        groupId = groupInfo.groupId,
                                        groupName = groupInfo.groupName,
                                        avatar = groupInfo.avatar,
                                        description = groupInfo.description,
                                        ownerId = groupInfo.ownerId,
                                        memberCount = groupInfo.memberCount,
                                        maxMemberCount = groupInfo.maxMemberCount,
                                        createdAt = groupInfo.createdAt,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    groupDao.insertGroup(groupEntity)
                                    
                                    updatedConversation = updatedConversation.copy(
                                        type = com.tongxun.data.local.entity.ConversationType.GROUP,
                                        targetId = conversationId,
                                        targetName = groupInfo.groupName,
                                        targetAvatar = groupInfo.avatar
                                    )
                                    Log.e(TAG, "✅✅✅ 修正：确实是群组，已更新会话信息 - conversationId=$conversationId, groupName=${groupInfo.groupName}")
                                    // 使用更新后的isGroup标志，不再执行单聊逻辑
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "重新检查群组信息失败 - conversationId: $conversationId", e)
                        }
                    }
                    
                    // 如果仍然是单聊，确保使用对方信息，而不是发送者信息
                    if (updatedConversation.type != com.tongxun.data.local.entity.ConversationType.GROUP) {
                        if (updatedConversation.type != com.tongxun.data.local.entity.ConversationType.SINGLE) {
                            // 提取对方ID
                            val userIds = conversationId.split("_")
                            val otherUserId = if (userIds.size == 2) {
                                userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                            } else {
                                messageDto.senderId
                            }
                            
                            updatedConversation = updatedConversation.copy(
                                type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                                targetId = otherUserId
                            )
                            Log.w(TAG, "⚠️ 修正会话类型为单聊 - conversationId=$conversationId, targetId=$otherUserId")
                        }
                        
                        // 如果targetName是发送者名称，需要更新为对方名称
                        val otherUserId = updatedConversation.targetId
                        val otherUser = userDao.getUserById(otherUserId)
                        if (otherUser != null && updatedConversation.targetName != otherUser.nickname) {
                            // 检查是否是发送者的姓名
                            if (updatedConversation.targetName == senderUser?.nickname) {
                                Log.w(TAG, "⚠️ 发现targetName是发送者姓名，更正为对方姓名 - conversationId=$conversationId, 原来=${updatedConversation.targetName}, 更正为=${otherUser.nickname}")
                            }
                            updatedConversation = updatedConversation.copy(
                                targetName = otherUser.nickname,
                                targetAvatar = otherUser.avatar
                            )
                            Log.e(TAG, "✅ 更新单聊会话对方信息 - conversationId=$conversationId, targetName=${otherUser.nickname}")
                        }
                    }
                }
                
                // 🔥 强制保存更新后的会话信息
                Log.e(TAG, "🔥🔥🔥 准备更新会话到数据库 - conversationId=$conversationId")
                Log.e(TAG, "   更新前: type=${finalConversation.type}, targetId=${finalConversation.targetId}, targetName=${finalConversation.targetName}")
                Log.e(TAG, "   更新后: type=${updatedConversation.type}, targetId=${updatedConversation.targetId}, targetName=${updatedConversation.targetName}")
                conversationDao.updateConversation(updatedConversation)
                
                // 验证更新是否成功
                val verifyConversation = conversationDao.getConversation(conversationId)
                Log.e(TAG, "✅✅✅ 接收消息的会话已强制更新到数据库 - conversationId=$conversationId")
                Log.e(TAG, "   验证: type=${verifyConversation?.type}, targetId=${verifyConversation?.targetId}, targetName=${verifyConversation?.targetName}")
                if (verifyConversation != null && verifyConversation.targetName != updatedConversation.targetName) {
                    Log.e(TAG, "❌❌❌ 警告：会话更新后验证失败！期望targetName=${updatedConversation.targetName}, 实际targetName=${verifyConversation.targetName}")
                }
            }
        }
    }
    
    // 处理撤回消息通知
    suspend fun handleMessageRecalled(messageId: String) {
        Log.d(TAG, "开始处理撤回消息 - messageId=$messageId")
        messageDao.recallMessage(messageId)
        Log.d(TAG, "消息已撤回 - messageId=$messageId")
    }
    
    override suspend fun fetchOfflineMessages(lastMessageTime: Long?): Result<List<MessageEntity>> {
        Log.e(TAG, "🔥🔥🔥 fetchOfflineMessages() 被调用 - lastMessageTime: $lastMessageTime")
        
        return try {
            Log.d(TAG, "准备发送HTTP请求拉取离线消息 - lastMessageTime: $lastMessageTime")
            Log.d(TAG, "请求URL: GET /api/messages/offline?lastMessageTime=$lastMessageTime")
            
            val offlineMessages = messageApi.getOfflineMessages(lastMessageTime)
            Log.e(TAG, "✅✅✅ 收到离线消息响应 - 消息数量: ${offlineMessages.size}")
            
            if (offlineMessages.isNotEmpty()) {
                Log.d(TAG, "第一条消息示例 - messageId: ${offlineMessages[0].messageId}, senderId: ${offlineMessages[0].senderId}, receiverId: ${offlineMessages[0].receiverId}")
            }
            
            val currentUser = authRepository.getCurrentUser()
            if (currentUser == null) {
                Log.e(TAG, "❌ 用户未登录，无法处理离线消息")
                return Result.failure(Exception("用户未登录"))
            }
            
            // 🔥 关键修复：过滤掉本地已删除的消息
            // 获取所有本地消息的 messageId，用于过滤
            val allLocalMessageIds = try {
                messageDao.getAllMessageIds()
            } catch (e: Exception) {
                Log.e(TAG, "❌ 获取本地消息ID列表失败", e)
                emptyList()
            }
            Log.e(TAG, "📋 本地消息总数: ${allLocalMessageIds.size}")
            
            // 获取已删除的消息ID列表（从SharedPreferences）
            val deletedMessageIds = getDeletedMessageIds()
            Log.e(TAG, "🗑️ 已删除的消息数量: ${deletedMessageIds.size}")
            if (deletedMessageIds.isNotEmpty()) {
                Log.e(TAG, "🗑️ 已删除的消息ID示例: ${deletedMessageIds.take(5).joinToString(", ")}")
            }
            
            // 过滤掉本地已存在的消息和已删除的消息（避免重复插入和重新拉取已删除的消息）
            val deletedInOffline = deletedMessageIds.intersect(offlineMessages.map { it.messageId }.toSet())
            val existingInOffline = allLocalMessageIds.intersect(offlineMessages.map { it.messageId }.toSet())
            
            Log.e(TAG, "📊 离线消息分析 - 总数: ${offlineMessages.size}, 本地已存在: ${existingInOffline.size}, 已删除: ${deletedInOffline.size}")
            if (deletedInOffline.isNotEmpty()) {
                Log.e(TAG, "🗑️ 离线消息中发现已删除的消息ID: ${deletedInOffline.take(10).joinToString(", ")}")
            }
            
            val newOfflineMessages = offlineMessages.filter { dto ->
                val exists = allLocalMessageIds.contains(dto.messageId)
                val isDeleted = deletedMessageIds.contains(dto.messageId)
                
                if (exists) {
                    Log.d(TAG, "⏭️ 跳过已存在的消息 - messageId: ${dto.messageId}, conversationId: ${dto.conversationId}")
                } else if (isDeleted) {
                    Log.e(TAG, "🗑️🗑️🗑️ 跳过已删除的消息（离线消息同步）- messageId: ${dto.messageId}, conversationId: ${dto.conversationId}, content: ${dto.content.take(30)}, senderId: ${dto.senderId}, receiverId: ${dto.receiverId}")
                    // 🔥 关键修复：如果发现已删除的消息，确保本地数据库中没有这条消息
                    try {
                        val localMessage = messageDao.getMessageById(dto.messageId)
                        if (localMessage != null) {
                            Log.w(TAG, "⚠️⚠️⚠️ 发现已删除的消息仍在本地数据库，立即删除 - messageId: ${dto.messageId}")
                            messageDao.deleteMessageById(dto.messageId)
                            Log.e(TAG, "✅ 已删除本地数据库中的已删除消息 - messageId: ${dto.messageId}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 删除本地已删除消息失败 - messageId: ${dto.messageId}", e)
                    }
                }
                
                !exists && !isDeleted
            }
            
            Log.e(TAG, "📋 过滤后需要插入的新消息数量: ${newOfflineMessages.size} (原始: ${offlineMessages.size}, 已存在: ${existingInOffline.size}, 已删除: ${deletedInOffline.size})")
            
            // 🔥 关键修复：修正错误的 conversationId（如果服务器返回的是 UUID 而不是正确的格式）
            val correctedMessages = newOfflineMessages.map { dto ->
                var correctedConversationId = dto.conversationId
                
                // 检查 conversationId 格式是否正确
                // 单聊应该是 "user1_user2" 格式（包含下划线）
                // 群聊应该是 UUID 格式（不包含下划线）
                val isUuidFormat = !dto.conversationId.contains("_") && 
                                   dto.conversationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE))
                
                // 如果 conversationId 是 UUID 格式，需要判断是群聊还是单聊
                // 如果是单聊，应该根据 senderId 和 receiverId 构建正确的 conversationId
                if (isUuidFormat) {
                    // 先检查是否是群聊（通过检查本地数据库或尝试获取群组信息）
                    val isGroup = try {
                        isGroupConversation(dto.conversationId)
                    } catch (e: Exception) {
                        false
                    }
                    
                    if (!isGroup) {
                        // 不是群聊，说明 conversationId 是错误的，需要根据 senderId 和 receiverId 重新构建
                        // 单聊的 conversationId 格式是 "user1_user2"（按字母顺序排序）
                        val userIds = listOf(dto.senderId, dto.receiverId).sorted()
                        
                        // 🔥 关键修复：过滤掉自己和自己对话的消息（senderId == receiverId）
                        if (dto.senderId == dto.receiverId) {
                            Log.w(TAG, "⚠️⚠️⚠️ 跳过自己和自己对话的消息 - messageId: ${dto.messageId}, senderId: ${dto.senderId}, receiverId: ${dto.receiverId}")
                            return@map null // 返回 null，稍后过滤掉
                        }
                        
                        correctedConversationId = "${userIds[0]}_${userIds[1]}"
                        
                        Log.w(TAG, "⚠️⚠️⚠️ 修正错误的单聊 conversationId - 原始: ${dto.conversationId}, 修正后: $correctedConversationId, senderId: ${dto.senderId}, receiverId: ${dto.receiverId}")
                        
                        // 删除错误的会话记录（如果存在）
                        try {
                            val wrongConversation = conversationDao.getConversation(dto.conversationId)
                            if (wrongConversation != null && wrongConversation.type == com.tongxun.data.local.entity.ConversationType.SINGLE) {
                                conversationDao.deleteConversationById(dto.conversationId)
                                Log.w(TAG, "🗑️ 已删除错误的会话记录 - conversationId: ${dto.conversationId}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "删除错误会话记录失败 - conversationId: ${dto.conversationId}", e)
                        }
                    }
                }
                
                dto?.copy(conversationId = correctedConversationId)
            }.filterNotNull() // 🔥 过滤掉自己和自己对话的消息
            
            Log.e(TAG, "📋 修正后的消息数量: ${correctedMessages.size} (原始: ${newOfflineMessages.size})")
            
            val messageEntities = correctedMessages.map { dto ->
                MessageEntity(
                    messageId = dto.messageId,
                    conversationId = dto.conversationId,
                    senderId = dto.senderId,
                    receiverId = dto.receiverId,
                    content = dto.content,
                    messageType = MessageType.valueOf(dto.messageType),
                    timestamp = dto.timestamp,
                    status = MessageStatus.valueOf(dto.status),
                    extra = dto.extra,
                    isRecalled = false
                )
            }
            
            // 🔥 关键修复：先获取服务器端的会话列表，只为服务器端存在的会话创建本地会话
            val serverConversations = try {
                conversationApi.getConversations()
            } catch (e: Exception) {
                Log.w(TAG, "获取服务器端会话列表失败，将允许创建所有会话", e)
                emptyList()
            }
            val serverConversationIds = serverConversations.map { it.conversationId }.toSet()
            Log.e(TAG, "📋 服务器端会话数量: ${serverConversationIds.size}, 会话ID列表: ${serverConversationIds.joinToString(", ") { it.take(16) + "..." }}")
            
            // 收集需要过滤的会话ID（服务器端不存在的会话）
            val conversationsToFilter = mutableSetOf<String>()
            
            // 先确保所有会话都存在（必须在插入消息之前，因为外键约束）
            val conversationIds = messageEntities.map { it.conversationId }.distinct()
            Log.e(TAG, "📋 需要检查的会话ID列表（去重后）: ${conversationIds.size} 个")
            conversationIds.forEachIndexed { index, conversationId ->
                Log.e(TAG, "📝 会话[$index] - conversationId: ${conversationId.take(32)}...")
                val existingConversation = conversationDao.getConversation(conversationId)
                if (existingConversation != null) {
                    Log.d(TAG, "✅ 会话已存在，跳过创建 - conversationId: ${conversationId.take(32)}..., type: ${existingConversation.type}, targetName: ${existingConversation.targetName}")
                } else {
                    // 🔥 关键修复：检查服务器端是否有这个会话，如果没有则不创建
                    if (serverConversationIds.isNotEmpty() && !serverConversationIds.contains(conversationId)) {
                        Log.w(TAG, "⚠️⚠️⚠️ 跳过创建会话（服务器端不存在）- conversationId: ${conversationId.take(32)}...")
                        // 收集需要过滤的会话ID
                        conversationsToFilter.add(conversationId)
                        Log.w(TAG, "🗑️ 将过滤掉该会话的消息，不保存到本地数据库")
                        return@forEachIndexed
                    }
                    
                    // 🔥 关键修复：先判断是否是群聊
                    val isGroup = isGroupConversation(conversationId)
                    Log.e(TAG, "🔥🔥🔥 为离线消息创建新会话 - conversationId=$conversationId, isGroup=$isGroup")
                    
                    // 🔥 获取该会话的最新消息时间戳，用于初始化 lastMessageTime
                    val messagesForConversation = messageEntities.filter { it.conversationId == conversationId }
                    val latestMessageTime = messagesForConversation.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                    
                    if (isGroup) {
                        // 🔥 关键修复：再次检查是否已存在（防止并发创建）
                        val doubleCheck = conversationDao.getConversation(conversationId)
                        if (doubleCheck != null) {
                            Log.w(TAG, "⚠️⚠️⚠️ 双重检查：会话已存在，跳过创建 - conversationId: ${conversationId.take(32)}..., type: ${doubleCheck.type}, targetName: ${doubleCheck.targetName}")
                            return@forEachIndexed
                        }
                        
                        // 群聊：获取群组信息（isGroupConversation已经确保本地数据库有群组信息）
                        val group = groupDao.getGroupById(conversationId)
                        val newConversation = com.tongxun.data.local.entity.ConversationEntity(
                            conversationId = conversationId,
                            type = com.tongxun.data.local.entity.ConversationType.GROUP,
                            targetId = conversationId, // 群聊的targetId就是群组ID
                            targetName = group?.groupName ?: "群聊",
                            targetAvatar = group?.avatar,
                            lastMessage = messagesForConversation.maxByOrNull { it.timestamp }?.let { msg ->
                                formatMessagePreview(MessageDto(
                                    messageId = msg.messageId,
                                    conversationId = msg.conversationId,
                                    senderId = msg.senderId,
                                    receiverId = msg.receiverId,
                                    content = msg.content,
                                    messageType = msg.messageType,
                                    timestamp = msg.timestamp,
                                    extra = msg.extra
                                ))
                            },
                            lastMessageTime = latestMessageTime, // 🔥 使用最新消息的时间戳，而不是 0
                            unreadCount = 0 // 稍后会更新
                        )
                        conversationDao.insertConversation(newConversation)
                        Log.e(TAG, "✅✅✅ 为离线群消息创建会话 - conversationId=$conversationId, groupName=${group?.groupName}, targetId=$conversationId, lastMessageTime=$latestMessageTime")
                        
                        // 🔥 验证创建是否成功
                        val verifyConversation = conversationDao.getConversation(conversationId)
                        if (verifyConversation == null) {
                            Log.e(TAG, "❌❌❌ 警告：群聊会话创建后验证失败！conversationId: $conversationId")
                        } else {
                            Log.d(TAG, "✅ 群聊会话创建验证成功 - conversationId: $conversationId, targetName: ${verifyConversation.targetName}")
                        }
                        
                        // 如果本地没有群组信息，异步获取并更新会话（不阻塞消息接收）
                        if (group == null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    Log.w(TAG, "本地没有群组信息，从服务器获取 - groupId=$conversationId")
                                    val groupResult = groupRepository.getGroupInfo(conversationId)
                                    if (groupResult.isSuccess) {
                                        val groupInfo = groupResult.getOrNull()
                                        groupInfo?.let { g ->
                                            val updatedConversation = newConversation.copy(
                                                targetName = g.groupName,
                                                targetAvatar = g.avatar
                                            )
                                            conversationDao.updateConversation(updatedConversation)
                                            Log.d(TAG, "离线消息会话群组信息已更新 - conversationId=$conversationId, groupName=${g.groupName}")
                                        }
                                    } else {
                                        Log.w(TAG, "获取群组信息失败 - groupId=$conversationId")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "获取群组信息失败 - groupId=$conversationId", e)
                                }
                            }
                        }
                    } else {
                        // 单聊：conversationId的格式是 "userA_userB"（排序后的两个用户ID）
                        val userIds = conversationId.split("_")
                        
                        // 🔥 关键修复：过滤掉自己和自己对话的会话（两个用户ID相同）
                        if (userIds.size == 2 && userIds[0] == userIds[1]) {
                            Log.w(TAG, "⚠️⚠️⚠️ 跳过自己和自己对话的会话 - conversationId: $conversationId")
                            return@forEachIndexed // 跳过这个会话
                        }
                        
                        val otherUserId = if (userIds.size == 2) {
                            userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                        } else {
                            // 如果格式不正确，尝试从消息中获取对方ID
                            val firstMessage = messageEntities.firstOrNull { it.conversationId == conversationId }
                            firstMessage?.let {
                                if (it.senderId == currentUser.userId) it.receiverId else it.senderId
                            } ?: userIds.firstOrNull() ?: ""
                        }
                        
                        // 🔥 再次检查：确保 otherUserId 不是当前用户ID
                        if (otherUserId == currentUser.userId || otherUserId.isEmpty()) {
                            Log.w(TAG, "⚠️⚠️⚠️ 跳过无效的单聊会话（otherUserId是当前用户或为空）- conversationId: $conversationId, otherUserId: $otherUserId")
                            return@forEachIndexed // 跳过这个会话
                        }
                        
                        // 尝试获取用户信息（先从本地，如果本地没有会从服务器获取）
                        val otherUser = userRepository.getUserById(otherUserId)
                        
                        // 🔥 获取该会话的最新消息时间戳，用于初始化 lastMessageTime
                        val messagesForConversation = messageEntities.filter { it.conversationId == conversationId }
                        val latestMessageTime = messagesForConversation.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                        
                        // 创建会话（如果用户信息不存在，使用临时名称，稍后异步更新）
                        val newConversation = com.tongxun.data.local.entity.ConversationEntity(
                            conversationId = conversationId,
                            type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                            targetId = otherUserId,
                            targetName = otherUser?.nickname ?: "用户",
                            targetAvatar = otherUser?.avatar,
                            lastMessage = messagesForConversation.maxByOrNull { it.timestamp }?.let { msg ->
                                formatMessagePreview(MessageDto(
                                    messageId = msg.messageId,
                                    conversationId = msg.conversationId,
                                    senderId = msg.senderId,
                                    receiverId = msg.receiverId,
                                    content = msg.content,
                                    messageType = msg.messageType,
                                    timestamp = msg.timestamp,
                                    extra = msg.extra
                                ))
                            },
                            lastMessageTime = latestMessageTime, // 🔥 使用最新消息的时间戳，而不是 0
                            unreadCount = 0 // 稍后会更新
                        )
                        conversationDao.insertConversation(newConversation)
                        Log.d(TAG, "为离线单聊消息创建会话 - conversationId=$conversationId, targetId=$otherUserId, targetName=${newConversation.targetName}, lastMessageTime=$latestMessageTime")
                        
                        // 如果用户信息不存在（用户名为"用户"），异步获取并更新
                        if (otherUser == null && newConversation.targetName == "用户") {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val remoteUser = userRepository.getUserById(otherUserId)
                                    remoteUser?.let { user ->
                                        val updatedConversation = newConversation.copy(
                                            targetName = user.nickname,
                                            targetAvatar = user.avatar
                                        )
                                        conversationDao.updateConversation(updatedConversation)
                                        Log.d(TAG, "会话用户信息已异步更新 - conversationId=$conversationId, targetName=${user.nickname}")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "异步获取用户信息失败 - userId=$otherUserId", e)
                                }
                            }
                        }
                    }
                }
            }
            
            // 🔥 关键修复：过滤掉服务器端不存在的会话的消息
            val filteredMessageEntities = if (conversationsToFilter.isNotEmpty()) {
                val beforeFilter = messageEntities.size
                val filtered = messageEntities.filter { !conversationsToFilter.contains(it.conversationId) }
                val afterFilter = filtered.size
                Log.w(TAG, "🗑️ 过滤掉服务器端不存在的会话的消息 - 过滤前: $beforeFilter, 过滤后: $afterFilter, 过滤的会话数: ${conversationsToFilter.size}")
                conversationsToFilter.forEach { conversationId ->
                    val filteredCount = messageEntities.count { it.conversationId == conversationId }
                    Log.w(TAG, "  会话 $conversationId 的消息数量: $filteredCount")
                }
                filtered
            } else {
                messageEntities
            }
            
            Log.d(TAG, "开始保存离线消息到本地数据库 - 消息数量: ${filteredMessageEntities.size}")
            // 过滤掉已存在的消息，避免重复
            val messagesToInsert = filteredMessageEntities.filter { message ->
                val existing = messageDao.getMessageById(message.messageId)
                if (existing != null) {
                    Log.d(TAG, "消息已存在，跳过 - messageId: ${message.messageId}")
                    false
                } else {
                    true
                }
            }
            Log.d(TAG, "过滤后需要插入的消息数量: ${messagesToInsert.size}")
            
            // 🔥 关键修复：在插入消息之前，确保所有会话都存在（外键约束要求）
            val conversationIdsToInsert = messagesToInsert.map { it.conversationId }.distinct()
            conversationIdsToInsert.forEach { conversationId ->
                val existingConversation = conversationDao.getConversation(conversationId)
                if (existingConversation == null) {
                    android.util.Log.e(TAG, "❌❌❌ 严重错误：离线消息的会话不存在，无法插入消息！conversationId: ${conversationId.take(32)}...")
                    android.util.Log.e(TAG, "   尝试创建会话...")
                    
                    // 紧急创建会话（使用第一条消息的信息）
                    val firstMessage = messagesToInsert.firstOrNull { it.conversationId == conversationId }
                    if (firstMessage != null) {
                        val isGroup = isGroupConversation(conversationId)
                        if (isGroup) {
                            val group = groupDao.getGroupById(conversationId)
                            val emergencyConversation = com.tongxun.data.local.entity.ConversationEntity(
                                conversationId = conversationId,
                                type = com.tongxun.data.local.entity.ConversationType.GROUP,
                                targetId = conversationId,
                                targetName = group?.groupName ?: "群聊",
                                targetAvatar = group?.avatar,
                                lastMessage = firstMessage.content.take(50),
                                lastMessageTime = firstMessage.timestamp,
                                unreadCount = 0
                            )
                            conversationDao.insertConversation(emergencyConversation)
                            android.util.Log.e(TAG, "✅ 紧急创建了群聊会话 - conversationId: ${conversationId.take(32)}...")
                        } else {
                            val userIds = listOf(firstMessage.senderId, firstMessage.receiverId).sorted()
                            val otherUserId = userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                            val otherUser = userDao.getUserById(otherUserId)
                            val emergencyConversation = com.tongxun.data.local.entity.ConversationEntity(
                                conversationId = conversationId,
                                type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                                targetId = otherUserId,
                                targetName = otherUser?.nickname ?: "用户",
                                targetAvatar = otherUser?.avatar,
                                lastMessage = firstMessage.content.take(50),
                                lastMessageTime = firstMessage.timestamp,
                                unreadCount = 0
                            )
                            conversationDao.insertConversation(emergencyConversation)
                            android.util.Log.e(TAG, "✅ 紧急创建了单聊会话 - conversationId: ${conversationId.take(32)}...")
                        }
                    }
                }
            }
            
            // 保存到本地数据库
            if (messagesToInsert.isNotEmpty()) {
                Log.e(TAG, "💾 准备保存 ${messagesToInsert.size} 条离线消息到本地数据库")
                if (messagesToInsert.isNotEmpty()) {
                    Log.e(TAG, "📝 第一条消息 - messageId: ${messagesToInsert[0].messageId}, conversationId: ${messagesToInsert[0].conversationId}, content: ${messagesToInsert[0].content.take(30)}")
                    Log.e(TAG, "📝 最后一条消息 - messageId: ${messagesToInsert.last().messageId}, conversationId: ${messagesToInsert.last().conversationId}, content: ${messagesToInsert.last().content.take(30)}")
                }
                try {
                    messageDao.insertMessages(messagesToInsert)
                    Log.e(TAG, "✅✅✅ 离线消息已保存到本地数据库 - 消息数量: ${messagesToInsert.size}")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌❌❌ 插入离线消息失败 - 消息数量: ${messagesToInsert.size}", e)
                    
                    // 检查哪些会话不存在
                    val failedConversationIds = messagesToInsert.map { it.conversationId }.distinct()
                    failedConversationIds.forEach { conversationId ->
                        val verifyConversation = conversationDao.getConversation(conversationId)
                        if (verifyConversation == null) {
                            android.util.Log.e(TAG, "❌❌❌ 会话不存在！conversationId: ${conversationId.take(32)}...")
                        }
                    }
                    
                    throw e // 重新抛出异常
                }
                
                // 验证保存是否成功
                val verifyCount = messageDao.getAllMessageIds().size
                Log.e(TAG, "📊 验证：本地数据库当前消息总数: $verifyCount")
            }
            
            // 更新会话的最后一条消息和时间
            filteredMessageEntities.groupBy { it.conversationId }.forEach { (conversationId, messages) ->
                val latestMessage = messages.maxByOrNull { it.timestamp }
                latestMessage?.let { msg ->
                    val conversation = conversationDao.getConversation(conversationId)
                    if (conversation != null) {
                        // 🔥 关键修复：先判断是否是群聊
                        val isGroup = isGroupConversation(conversationId)
                        
                        var updatedConversation = conversation.copy(
                            lastMessage = formatMessagePreview(MessageDto(
                                messageId = msg.messageId,
                                conversationId = msg.conversationId,
                                senderId = msg.senderId,
                                receiverId = msg.receiverId,
                                content = msg.content,
                                messageType = msg.messageType,
                                timestamp = msg.timestamp,
                                extra = msg.extra
                            )),
                            lastMessageTime = msg.timestamp
                        )
                        
                        // 🔥 如果是群聊，确保使用群组信息
                        if (isGroup) {
                            // 确保会话类型是GROUP
                            if (updatedConversation.type != com.tongxun.data.local.entity.ConversationType.GROUP) {
                                updatedConversation = updatedConversation.copy(
                                    type = com.tongxun.data.local.entity.ConversationType.GROUP,
                                    targetId = conversationId
                                )
                                Log.w(TAG, "⚠️ 修正离线消息会话类型为群聊 - conversationId=$conversationId")
                            }
                            
                            // 强制使用群组名称和头像
                            val group = groupDao.getGroupById(conversationId)
                            if (group != null) {
                                updatedConversation = updatedConversation.copy(
                                    type = com.tongxun.data.local.entity.ConversationType.GROUP,
                                    targetId = conversationId,
                                    targetName = group.groupName,
                                    targetAvatar = group.avatar
                                )
                                Log.e(TAG, "✅ 更新离线消息会话群组信息 - conversationId=$conversationId, groupName=${group.groupName}")
                            }
                        }
                        
                        // 如果是群聊且群组名称为空，尝试更新群组信息
                        if (updatedConversation.type == com.tongxun.data.local.entity.ConversationType.GROUP && 
                            (updatedConversation.targetName.isBlank() || updatedConversation.targetName == "群聊")) {
                            val group = groupDao.getGroupById(conversationId)
                            if (group != null) {
                                updatedConversation = updatedConversation.copy(
                                    targetName = group.groupName,
                                    targetAvatar = group.avatar
                                )
                                Log.d(TAG, "更新会话群组信息 - conversationId=$conversationId, groupName=${group.groupName}")
                            } else {
                                // 如果本地没有，异步获取
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val groupResult = groupRepository.getGroupInfo(conversationId)
                                        if (groupResult.isSuccess) {
                                            val groupInfo = groupResult.getOrNull()
                                            groupInfo?.let { g ->
                                                val furtherUpdated = updatedConversation.copy(
                                                    targetName = g.groupName,
                                                    targetAvatar = g.avatar
                                                )
                                                conversationDao.updateConversation(furtherUpdated)
                                                Log.d(TAG, "异步更新会话群组信息 - conversationId=$conversationId, groupName=${g.groupName}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "异步获取群组信息失败 - groupId=$conversationId", e)
                                    }
                                }
                            }
                        }
                        
                        conversationDao.updateConversation(updatedConversation)
                    }
                }
            }
            
            // 更新会话未读数
            messageEntities.groupBy { it.conversationId }.forEach { (conversationId, messages) ->
                val unreadCount = messages.count { it.receiverId == currentUser.userId && it.status != MessageStatus.READ }
                if (unreadCount > 0) {
                    conversationDao.increaseUnreadCount(conversationId, unreadCount)
                    Log.d(TAG, "会话未读数已更新 - conversationId: $conversationId, unreadCount: $unreadCount")
                }
            }
            
            Result.success(messageEntities)
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ 拉取离线消息失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 🔥 关键修复：修正所有单聊消息的conversationId
     * 这个方法会修正数据库中所有使用错误conversationId的单聊消息
     */
    suspend fun fixSingleChatMessages(): Result<Int> {
        return try {
            android.util.Log.e(TAG, "🔥🔥🔥 开始修复单聊消息的conversationId...")
            
            val currentUser = authRepository.getCurrentUser()
            if (currentUser == null) {
                android.util.Log.e(TAG, "❌ 用户未登录，无法修复消息")
                return Result.failure(Exception("用户未登录"))
            }
            
            // 获取所有消息
            val allMessages = messageDao.getAllMessages()
            android.util.Log.e(TAG, "📋 总共有 ${allMessages.size} 条消息需要检查")
            
            var fixedCount = 0
            val messagesToUpdate = mutableListOf<MessageEntity>()
            val wrongConversationIds = mutableSetOf<String>()
            
            allMessages.forEach { message ->
                // 🔥 关键修复：先检查是否是群聊消息，避免错误修正群聊消息
                val isGroupChat = try {
                    isGroupConversation(message.conversationId)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "检查群聊失败 - conversationId: ${message.conversationId.take(16)}...", e)
                    false
                }
                
                // 如果是群聊，跳过（不修正）
                if (isGroupChat) {
                    android.util.Log.d(TAG, "跳过群聊消息（不修正）- messageId: ${message.messageId.take(8)}..., conversationId: ${message.conversationId.take(16)}...")
                    return@forEach
                }
                
                // 检查是否是单聊消息
                // 单聊消息的conversationId应该是 "userA_userB" 格式（包含下划线且不是群组ID）
                val isSingleChat = try {
                    // 如果conversationId不包含下划线，可能是错误的单聊消息
                    // 如果包含下划线，检查是否是群组ID
                    if (!message.conversationId.contains("_")) {
                        // 不包含下划线，可能是错误的单聊消息（应该是UUID格式的群聊，但这里判断为单聊）
                        // 根据senderId和receiverId判断
                        message.senderId != message.receiverId && 
                        message.senderId != "" && 
                        message.receiverId != ""
                    } else {
                        // 包含下划线，检查是否是群组ID（理论上不应该，但为了安全还是检查）
                        !isGroupConversation(message.conversationId)
                    }
                } catch (e: Exception) {
                    // 如果判断失败，根据conversationId格式判断
                    message.conversationId.contains("_")
                }
                
                if (isSingleChat) {
                    // 根据senderId和receiverId构建正确的conversationId
                    val userIds = listOf(message.senderId, message.receiverId).sorted()
                    val correctConversationId = "${userIds[0]}_${userIds[1]}"
                    
                    // 如果conversationId不正确，需要修正
                    if (message.conversationId != correctConversationId) {
                        android.util.Log.e(TAG, "❌❌❌ 发现错误的单聊消息 - messageId: ${message.messageId.take(8)}..., 原始conversationId: ${message.conversationId.take(32)}..., 正确conversationId: ${correctConversationId.take(32)}..., senderId: ${message.senderId.take(8)}..., receiverId: ${message.receiverId.take(8)}...")
                        
                        // 更新消息的conversationId
                        val fixedMessage = message.copy(conversationId = correctConversationId)
                        messagesToUpdate.add(fixedMessage)
                        fixedCount++
                        
                        // 记录错误的conversationId，稍后删除错误的会话
                        wrongConversationIds.add(message.conversationId)
                        
                        // 确保正确的会话存在
                        val correctConversation = conversationDao.getConversation(correctConversationId)
                        if (correctConversation == null) {
                            // 会话不存在，创建新会话
                            val otherUserId = userIds.firstOrNull { it != currentUser.userId } ?: userIds.first()
                            val otherUser = userRepository.getUserById(otherUserId)
                            
                            val newConversation = com.tongxun.data.local.entity.ConversationEntity(
                                conversationId = correctConversationId,
                                type = com.tongxun.data.local.entity.ConversationType.SINGLE,
                                targetId = otherUserId,
                                targetName = otherUser?.nickname ?: "用户",
                                targetAvatar = otherUser?.avatar,
                                lastMessage = message.content.take(50),
                                lastMessageTime = message.timestamp,
                                unreadCount = 0
                            )
                            conversationDao.insertConversation(newConversation)
                            android.util.Log.e(TAG, "✅ 创建了正确的会话 - conversationId: ${correctConversationId.take(16)}..., targetId: ${otherUserId.take(8)}..., targetName: ${otherUser?.nickname ?: "用户"}")
                        }
                    }
                }
            }
            
            // 批量更新消息
            if (messagesToUpdate.isNotEmpty()) {
                android.util.Log.e(TAG, "📝 开始批量更新 ${messagesToUpdate.size} 条消息...")
                messagesToUpdate.forEach { message ->
                    messageDao.updateMessage(message)
                }
                android.util.Log.e(TAG, "✅✅✅ 已修复 $fixedCount 条单聊消息的conversationId")
            } else {
                android.util.Log.e(TAG, "✅ 没有发现需要修复的消息")
            }
            
            // 删除错误的会话（如果该会话下没有消息了）
            wrongConversationIds.forEach { wrongConversationId ->
                try {
                    val messagesInWrongConversation = messageDao.getMessages(wrongConversationId, limit = 1, offset = 0)
                    if (messagesInWrongConversation.isEmpty()) {
                        // 该会话下没有消息了，可以安全删除
                        val wrongConversation = conversationDao.getConversation(wrongConversationId)
                        if (wrongConversation != null && wrongConversation.type == com.tongxun.data.local.entity.ConversationType.SINGLE) {
                            conversationDao.deleteConversationById(wrongConversationId)
                            android.util.Log.e(TAG, "🗑️ 已删除错误的会话（无消息）- conversationId: ${wrongConversationId.take(16)}...")
                        }
                    } else {
                        android.util.Log.w(TAG, "⚠️ 错误的会话仍有消息，暂不删除 - conversationId: ${wrongConversationId.take(16)}..., 消息数量: ${messagesInWrongConversation.size}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "删除错误会话失败 - conversationId: ${wrongConversationId.take(16)}...", e)
                }
            }
            
            Result.success(fixedCount)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌❌❌ 修复单聊消息失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getMessageReadStats(messageId: String): Result<com.tongxun.data.remote.dto.MessageReadStatsDto> {
        return try {
            val stats = messageApi.getMessageReaders(messageId)
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 将MarkAsReadRequest移到这里避免循环依赖
data class MarkAsReadRequest(
    @SerializedName("conversationId")
    val conversationId: String,
    @SerializedName("messageIds")
    val messageIds: List<String>? = null
)
