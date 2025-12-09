package com.tongxun.data.repository

import android.util.Log
import com.tongxun.data.local.TongxunDatabase
import com.tongxun.data.local.entity.ConversationEntity
import com.tongxun.data.local.entity.ConversationType
import com.tongxun.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val database: TongxunDatabase,
    private val authRepository: com.tongxun.domain.repository.AuthRepository,
    private val userRepository: com.tongxun.domain.repository.UserRepository,
    private val groupRepository: com.tongxun.domain.repository.GroupRepository,
    private val conversationApi: com.tongxun.data.remote.api.ConversationApi
) : ConversationRepository {
    
    private val TAG = "ConversationRepository"
    private val conversationDao = database.conversationDao()
    
    // 用于异步更新用户信息的协程作用域
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 用于跟踪正在更新的会话，避免重复更新（使用线程安全的集合）
    private val updatingConversations = ConcurrentHashMap.newKeySet<String>()
    
    override fun getAllConversations(): Flow<List<ConversationEntity>> {
        val currentUser = authRepository.getCurrentUser()
        Log.d(TAG, "getAllConversations() - currentUser: ${currentUser?.userId?.take(8)}...")
        
        return conversationDao.getAllConversations()
            .distinctUntilChanged() // 只在数据真正变化时触发
            .map { conversations ->
                // 🔥 关键修复：首先在数据库层面去重（虽然 conversationId 是主键，不应该有重复，但为了安全还是处理）
                val uniqueConversations = conversations.distinctBy { it.conversationId }
                if (uniqueConversations.size != conversations.size) {
                    Log.e(TAG, "❌❌❌ 发现数据库中有重复的会话记录！原始数量: ${conversations.size}, 去重后: ${uniqueConversations.size}")
                    // 如果发现重复，记录详细信息
                    val duplicates = conversations.groupingBy { it.conversationId }.eachCount().filter { it.value > 1 }
                    duplicates.forEach { (conversationId, count) ->
                        Log.e(TAG, "  重复的 conversationId: ${conversationId.take(32)}..., 重复次数: $count")
                    }
                }
                
                // 使用去重后的列表继续处理
                val conversationsToProcess = uniqueConversations
                Log.e(TAG, "========== 🔥🔥🔥 开始处理会话列表 ==========")
                Log.e(TAG, "📋 从数据库获取到 ${conversations.size} 个会话（处理前）")
                
                // 详细记录每个会话的信息
                conversationsToProcess.forEachIndexed { index, conv ->
                    Log.e(TAG, "📝 会话[$index] - conversationId: ${conv.conversationId.take(16)}..., type: ${conv.type}, targetId: ${conv.targetId.take(16)}..., targetName: ${conv.targetName}, lastMessageTime: ${conv.lastMessageTime}")
                }
                
                if (currentUser == null) {
                    Log.w(TAG, "用户未登录，返回空列表")
                    emptyList()
                } else {
                    // 按conversationId去重，确保每个conversationId只返回一条记录
                    // 使用distinctBy确保每个conversationId只有一条记录
                    val grouped = conversationsToProcess.groupBy { it.conversationId }
                    Log.e(TAG, "📊 按conversationId分组后，共有 ${grouped.size} 个不同的conversationId")
                    
                    grouped.forEach { (conversationId, list) ->
                        if (list.size > 1) {
                            Log.e(TAG, "⚠️⚠️⚠️ 发现重复的conversationId: $conversationId, 重复数量: ${list.size}")
                            list.forEachIndexed { index, conv ->
                                Log.e(TAG, "  重复[$index] - type: ${conv.type}, targetId: ${conv.targetId.take(16)}..., targetName: ${conv.targetName}, lastMessageTime: ${conv.lastMessageTime}")
                            }
                            
                            // 🔥 如果是群聊，记录详细信息
                            val groupConversations = list.filter { it.type == ConversationType.GROUP }
                            if (groupConversations.size > 1) {
                                Log.e(TAG, "❌❌❌ 发现重复的群聊会话！conversationId: $conversationId, 重复数量: ${groupConversations.size}")
                                groupConversations.forEachIndexed { index, conv ->
                                    Log.e(TAG, "  群聊重复[$index] - targetId: ${conv.targetId.take(16)}..., targetName: ${conv.targetName}, lastMessageTime: ${conv.lastMessageTime}")
                                }
                            }
                        }
                    }
                    
                    val deduplicated = grouped
                        .mapValues { (conversationId, list) ->
                            // 对于同一个conversationId，优先选择正确的记录
                            // 1. 对于群聊：选择 type == GROUP 且 targetId == conversationId 的记录
                            // 2. 对于单聊：选择 targetId != currentUser.userId 的记录
                            val groupConversations = list.filter { 
                                it.type == ConversationType.GROUP && 
                                it.targetId == conversationId 
                            }
                            if (groupConversations.isNotEmpty()) {
                                // 群聊：优先选择正确的群聊记录，选择最新的
                                val selected = groupConversations.maxByOrNull { it.lastMessageTime } ?: groupConversations.first()
                                Log.d(TAG, "✅ 群聊去重 - conversationId: $conversationId, 选择了: targetName=${selected.targetName}, lastMessageTime=${selected.lastMessageTime}")
                                selected
                            } else {
                                // 单聊：选择 targetId != currentUser.userId 的记录
                                val singleConversations = list.filter { 
                                    it.type == ConversationType.SINGLE && 
                                    it.targetId != currentUser.userId 
                                }
                                if (singleConversations.isNotEmpty()) {
                                    val selected = singleConversations.maxByOrNull { it.lastMessageTime } ?: singleConversations.first()
                                    Log.d(TAG, "✅ 单聊去重 - conversationId: $conversationId, 选择了: targetName=${selected.targetName}, targetId=${selected.targetId.take(16)}..., lastMessageTime=${selected.lastMessageTime}")
                                    selected
                                } else {
                                    // 如果都不符合，返回最新的（但不应该发生）
                                    val selected = list.maxByOrNull { it.lastMessageTime } ?: list.first()
                                    Log.w(TAG, "⚠️ 不符合群聊或单聊条件，选择最新的 - conversationId: $conversationId, type: ${selected.type}, targetId: ${selected.targetId.take(16)}...")
                                    selected
                                }
                            }
                        }
                        .values
                    
                    Log.e(TAG, "📊 去重后共有 ${deduplicated.size} 个会话")
                    
                    val filtered = deduplicated.filter { conversation ->
                        // 对于群聊，确保 targetId == conversationId（群组ID）
                        // 对于单聊，确保 targetId != currentUser.userId
                        val shouldKeep = when (conversation.type) {
                            ConversationType.GROUP -> conversation.targetId == conversation.conversationId
                            ConversationType.SINGLE -> conversation.targetId != currentUser.userId
                        }
                        if (!shouldKeep) {
                            Log.w(TAG, "🗑️ 过滤掉无效会话 - conversationId: ${conversation.conversationId.take(16)}..., type: ${conversation.type}, targetId: ${conversation.targetId.take(16)}..., targetName: ${conversation.targetName}")
                        }
                        shouldKeep
                    }
                    
                    Log.e(TAG, "📊 过滤后共有 ${filtered.size} 个会话")
                    
                    // 🔥 关键修复：使用 LinkedHashMap 确保去重，保留第一个出现的会话（按时间排序后，第一个就是最新的）
                    val uniqueConversations = LinkedHashMap<String, ConversationEntity>()
                    filtered.forEach { conversation ->
                        // 如果 conversationId 已存在，比较 lastMessageTime，保留时间更新的
                        val existing = uniqueConversations[conversation.conversationId]
                        if (existing == null) {
                            uniqueConversations[conversation.conversationId] = conversation
                        } else {
                            // 如果已存在，比较时间，保留时间更新的
                            if (conversation.lastMessageTime > existing.lastMessageTime) {
                                Log.w(TAG, "⚠️ 发现重复的 conversationId，保留时间更新的 - conversationId: ${conversation.conversationId.take(16)}..., 旧时间: ${existing.lastMessageTime}, 新时间: ${conversation.lastMessageTime}")
                                uniqueConversations[conversation.conversationId] = conversation
                            } else {
                                Log.w(TAG, "⚠️ 发现重复的 conversationId，保留已存在的（时间更晚）- conversationId: ${conversation.conversationId.take(16)}..., 已存在时间: ${existing.lastMessageTime}, 新时间: ${conversation.lastMessageTime}")
                            }
                        }
                    }
                    
                    val finalList = uniqueConversations.values
                        .sortedWith(compareByDescending<ConversationEntity> { it.isTop }
                            .thenByDescending { it.lastMessageTime }) // 🔥 关键修复：先按 isTop 降序，再按 lastMessageTime 降序排序
                    
                    // 详细记录最终返回的会话
                    finalList.forEachIndexed { index, conv ->
                        Log.e(TAG, "✅ 最终会话[$index] - conversationId: ${conv.conversationId.take(16)}..., type: ${conv.type}, targetId: ${conv.targetId.take(16)}..., targetName: ${conv.targetName}, lastMessageTime: ${conv.lastMessageTime}")
                    }
                    
                    // 🔥 最终验证：确保没有重复的 conversationId
                    val conversationIds = finalList.map { it.conversationId }
                    val duplicateIds = conversationIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                    if (duplicateIds.isNotEmpty()) {
                        Log.e(TAG, "❌❌❌ 严重错误：最终列表仍有重复的 conversationId: ${duplicateIds.joinToString(", ")}")
                    } else {
                        Log.d(TAG, "✅ 最终验证通过：没有重复的 conversationId")
                    }
                    
                    Log.e(TAG, "========== 🔥🔥🔥 会话列表处理完成 ==========")
                    
                    // 🔥 关键修复：检查并修复 lastMessageTime 为 0 的会话（异步处理，不阻塞返回）
                    finalList.forEach { conversation ->
                        // 如果 lastMessageTime 为 0 或无效，尝试从本地消息数据库修复
                        if (conversation.lastMessageTime <= 0 && conversation.conversationId.isNotBlank()) {
                            val conversationId = conversation.conversationId
                            if (!updatingConversations.contains(conversationId)) {
                                updatingConversations.add(conversationId)
                                updateScope.launch {
                                    try {
                                        // 从本地消息数据库获取最新消息的时间戳
                                        val messages = database.messageDao().getMessages(conversationId, limit = 1, offset = 0)
                                        val latestMessage = messages.firstOrNull()
                                        if (latestMessage != null && latestMessage.timestamp > 0) {
                                            val updatedConversation = conversation.copy(
                                                lastMessageTime = latestMessage.timestamp,
                                                lastMessage = latestMessage.content.take(50) // 简单处理，实际应该格式化
                                            )
                                            conversationDao.updateConversation(updatedConversation)
                                            Log.w(TAG, "修复会话时间戳 - conversationId=$conversationId, lastMessageTime=${latestMessage.timestamp}")
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "修复会话时间戳失败 - conversationId=$conversationId", e)
                                    } finally {
                                        updatingConversations.remove(conversationId)
                                    }
                                }
                            }
                        }
                        
                        if (conversation.type == ConversationType.GROUP) {
                            // 群聊：检查群组名称是否正确
                            val isDateLikeName = isDateLike(conversation.targetName)
                            val needsUpdate = conversation.targetName.isBlank() || 
                                             conversation.targetName == "群聊" || 
                                             conversation.targetName == "未知用户" ||
                                             conversation.targetName == "用户" ||
                                             isDateLikeName ||
                                             conversation.targetId != conversation.conversationId
                            
                            if (needsUpdate && conversation.conversationId.isNotBlank()) {
                                // 检查是否正在更新此会话，避免重复更新
                                val conversationId = conversation.conversationId
                                if (updatingConversations.contains(conversationId)) {
                                    // 正在更新中，跳过（不在日志中打印，避免日志刷屏）
                                    return@forEach
                                }
                                
                                // 使用同步方式添加到集合，确保只有一个协程处理此会话
                                val wasAdded = updatingConversations.add(conversationId)
                                if (!wasAdded) {
                                    // 如果添加失败（理论上不应该发生），跳过
                                    return@forEach
                                }
                                
                                // 异步获取群组信息并更新会话
                                updateScope.launch {
                                    try {
                                        // 如果名称像日期或本地数据明显错误，直接从服务器获取
                                        if (isDateLikeName || conversation.targetName.isBlank() || conversation.targetName == "群聊") {
                                            Log.w(TAG, "检测到错误的群组名称，从服务器获取 - conversationId=$conversationId, currentName=${conversation.targetName}")
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
                                                    database.groupDao().insertGroup(groupEntity)
                                                    
                                                    val updatedConversation = conversation.copy(
                                                        type = ConversationType.GROUP,
                                                        targetId = conversationId,
                                                        targetName = g.groupName,
                                                        targetAvatar = g.avatar
                                                    )
                                                    conversationDao.updateConversation(updatedConversation)
                                                    Log.d(TAG, "会话群组信息已更新（服务器） - conversationId=$conversationId, groupName=${g.groupName}")
                                                }
                                            } else {
                                                // 检查是否是 404 错误（群组不存在）
                                                val exception = groupResult.exceptionOrNull()
                                                if (exception is retrofit2.HttpException && exception.code() == 404) {
                                                    Log.w(TAG, "群组不存在（404），删除会话 - conversationId=$conversationId")
                                                    // 群组不存在，删除该会话
                                                    try {
                                                        conversationDao.deleteConversationById(conversationId)
                                                        Log.d(TAG, "已删除不存在的群组会话 - conversationId=$conversationId")
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "删除会话失败 - conversationId=$conversationId", e)
                                                    }
                                                } else {
                                                    Log.w(TAG, "获取群组信息失败 - conversationId=$conversationId, error: ${exception?.message}")
                                                }
                                            }
                                        } else {
                                            // 先尝试从本地数据库获取（但要检查本地数据是否也是错误的）
                                            val group = database.groupDao().getGroupById(conversationId)
                                            if (group != null && !isDateLike(group.groupName) && group.groupName.isNotBlank()) {
                                                // 本地数据正确，直接使用
                                                val updatedConversation = conversation.copy(
                                                    type = ConversationType.GROUP,
                                                    targetId = conversationId,
                                                    targetName = group.groupName,
                                                    targetAvatar = group.avatar
                                                )
                                                conversationDao.updateConversation(updatedConversation)
                                                Log.d(TAG, "会话群组信息已更新（本地） - conversationId=$conversationId, groupName=${group.groupName}")
                                            } else {
                                                // 如果本地没有或本地数据也错误，从服务器获取
                                                Log.w(TAG, "本地群组数据也错误或不存在，从服务器获取 - conversationId=$conversationId")
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
                                                        database.groupDao().insertGroup(groupEntity)
                                                        
                                                        val updatedConversation = conversation.copy(
                                                            type = ConversationType.GROUP,
                                                            targetId = conversationId,
                                                            targetName = g.groupName,
                                                            targetAvatar = g.avatar
                                                        )
                                                        conversationDao.updateConversation(updatedConversation)
                                                        Log.d(TAG, "会话群组信息已更新（服务器） - conversationId=$conversationId, groupName=${g.groupName}")
                                                    }
                                                } else {
                                                    // 检查是否是 404 错误（群组不存在）
                                                    val exception = groupResult.exceptionOrNull()
                                                    if (exception is retrofit2.HttpException && exception.code() == 404) {
                                                        Log.w(TAG, "群组不存在（404），删除会话 - conversationId=$conversationId")
                                                        // 群组不存在，删除该会话
                                                        try {
                                                            conversationDao.deleteConversationById(conversationId)
                                                            Log.d(TAG, "已删除不存在的群组会话 - conversationId=$conversationId")
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "删除会话失败 - conversationId=$conversationId", e)
                                                        }
                                                    } else {
                                                        Log.w(TAG, "获取群组信息失败 - conversationId=$conversationId, error: ${exception?.message}")
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "更新会话群组信息失败 - conversationId=$conversationId", e)
                                    } finally {
                                        // 更新完成后，从更新列表中移除（必须执行，否则会永久阻塞）
                                        updatingConversations.remove(conversationId)
                                    }
                                }
                            }
                        } else {
                            // 单聊：检查并更新用户名为空、"用户"或"未知用户"的会话，或头像为空的会话
                            val needsUpdate = conversation.targetId.isNotBlank() && (
                                conversation.targetName.isBlank() || 
                                conversation.targetName == "用户" || 
                                conversation.targetName == "未知用户" ||
                                conversation.targetAvatar.isNullOrBlank()
                            )
                            
                            android.util.Log.e(TAG, "🔥 检查单聊会话是否需要更新 - conversationId=${conversation.conversationId.take(8)}..., targetId=${conversation.targetId.take(8)}..., targetName='${conversation.targetName}', targetAvatar=${if (conversation.targetAvatar.isNullOrBlank()) "null/empty" else "exists"}, needsUpdate=$needsUpdate")
                            
                            if (needsUpdate) {
                                // 检查是否正在更新此会话，避免重复更新
                                val conversationId = conversation.conversationId
                                if (updatingConversations.contains(conversationId)) {
                                    // 正在更新中，跳过
                                    return@forEach
                                }
                                
                                // 使用同步方式添加到集合，确保只有一个协程处理此会话
                                val wasAdded = updatingConversations.add(conversationId)
                                if (!wasAdded) {
                                    // 如果添加失败，跳过
                                    return@forEach
                                }
                                
                                // 异步获取用户信息并更新会话（使用独立的协程作用域，避免影响Flow）
                                updateScope.launch {
                                    try {
                                        android.util.Log.e(TAG, "🔥🔥🔥 开始更新单聊会话用户信息 - conversationId=${conversation.conversationId}, targetId=${conversation.targetId.take(8)}..., currentTargetName='${conversation.targetName}', currentTargetAvatar=${if (conversation.targetAvatar.isNullOrBlank()) "null/empty" else conversation.targetAvatar.take(20) + "..."}")
                                        
                                        val targetUser = userRepository.getUserById(conversation.targetId)
                                        
                                        if (targetUser != null) {
                                            android.util.Log.e(TAG, "✅✅✅ 获取到用户信息 - userId=${targetUser.userId.take(8)}..., nickname=${targetUser.nickname}, avatar=${targetUser.avatar?.take(20) ?: "null"}...")
                                            
                                            val updatedConversation = conversation.copy(
                                                targetName = targetUser.nickname,
                                                targetAvatar = targetUser.avatar
                                            )
                                            conversationDao.updateConversation(updatedConversation)
                                            
                                            android.util.Log.e(TAG, "✅✅✅ 会话用户信息已更新 - conversationId=${conversation.conversationId}, targetName=${targetUser.nickname}, targetAvatar=${targetUser.avatar?.take(20) ?: "null"}...")
                                        } else {
                                            android.util.Log.e(TAG, "❌❌❌ 无法获取用户信息 - conversationId=${conversation.conversationId}, targetId=${conversation.targetId.take(8)}...")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e(TAG, "❌❌❌ 更新会话用户信息失败 - conversationId=${conversation.conversationId}, targetId=${conversation.targetId.take(8)}...", e)
                                    } finally {
                                        // 更新完成后，从更新列表中移除（必须执行，否则会永久阻塞）
                                        updatingConversations.remove(conversationId)
                                    }
                                }
                            }
                        }
                    }
                    
                    // 🔥 关键修复：按置顶和时间排序（使用 finalList 而不是 deduplicated）
                    val sorted = finalList.sortedWith(
                        compareByDescending<ConversationEntity> { it.isTop }
                            .thenByDescending { it.lastMessageTime }
                    )
                    Log.d(TAG, "处理后返回 ${sorted.size} 个会话")
                    sorted
                }
            }
    }
    
    override suspend fun getConversationById(conversationId: String): ConversationEntity? {
        Log.d(TAG, "getConversationById() - conversationId: ${conversationId.take(16)}...")
        return conversationDao.getConversation(conversationId)
    }
    
    override suspend fun getOrCreateConversation(
        targetId: String,
        type: ConversationType
    ): ConversationEntity {
        val currentUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("用户未登录")
        
        val conversationId = if (type == ConversationType.SINGLE) {
            // 单聊：使用两个用户ID排序后拼接
            listOf(currentUser.userId, targetId).sorted().joinToString("_")
        } else {
            // 群聊：使用群组ID
            targetId
        }
        
        val existing = conversationDao.getConversation(conversationId)
        if (existing != null) {
            return existing
        }
        
        // 获取目标信息
        val targetName: String
        val targetAvatar: String?
        
        if (type == ConversationType.SINGLE) {
            // 单聊：获取用户信息
            val targetUser = userRepository.getUserById(targetId)
            targetName = targetUser?.nickname ?: ""
            targetAvatar = targetUser?.avatar
        } else {
            // 群聊：从本地数据库获取群组信息
            val group = database.groupDao().getGroupById(targetId)
            targetName = group?.groupName ?: "群聊"
            targetAvatar = group?.avatar
            Log.d(TAG, "创建群聊会话 - groupId: $targetId, groupName: $targetName")
        }
        
        val newConversation = ConversationEntity(
            conversationId = conversationId,
            type = type,
            targetId = targetId,
            targetName = targetName,
            targetAvatar = targetAvatar
        )
        
        conversationDao.insertConversation(newConversation)
        return newConversation
    }
    
    override suspend fun updateConversation(conversation: ConversationEntity) {
        conversationDao.updateConversation(conversation)
    }
    
    override suspend fun deleteConversation(conversationId: String) {
        Log.e(TAG, "========== 🔥🔥🔥 开始删除会话 ==========")
        Log.e(TAG, "conversationId: $conversationId")
        
        // 先获取会话信息，用于日志记录
        val conversation = conversationDao.getConversation(conversationId)
        if (conversation != null) {
            Log.e(TAG, "会话信息 - type: ${conversation.type}, targetId: ${conversation.targetId}, targetName: ${conversation.targetName}")
        } else {
            Log.w(TAG, "⚠️ 本地未找到会话记录 - conversationId: $conversationId")
            // 即使本地没有，也尝试删除服务器端的记录
        }
        
        // 🔥 关键修复：先删除本地记录，再删除服务器端记录
        // 这样可以确保即使服务器删除失败，本地也已经删除了
        var localDeleteSuccess = false
        try {
            Log.e(TAG, "🗑️ 开始删除本地会话 - conversationId: $conversationId")
            val deleteCount = conversationDao.deleteConversationById(conversationId)
            Log.e(TAG, "🗑️ Room删除操作返回 - deleteCount: $deleteCount (如果返回0表示没有找到记录)")
            
            // 等待一小段时间，确保Room数据库操作完成
            kotlinx.coroutines.delay(100)
            
            // 验证删除是否成功
            val verifyConversation = conversationDao.getConversation(conversationId)
            if (verifyConversation == null) {
                localDeleteSuccess = true
                Log.e(TAG, "✅✅✅ 本地会话删除成功 - conversationId: $conversationId, deleteCount: $deleteCount")
            } else {
                Log.e(TAG, "❌❌❌ 本地会话删除失败！会话仍然存在 - conversationId: $conversationId, deleteCount: $deleteCount")
                Log.e(TAG, "   会话信息 - type: ${verifyConversation.type}, targetId: ${verifyConversation.targetId}, targetName: ${verifyConversation.targetName}")
                
                // 重试删除（使用不同的方法）
                try {
                    Log.e(TAG, "🔄 重试删除本地会话 - conversationId: $conversationId")
                    if (verifyConversation != null) {
                        conversationDao.deleteConversation(verifyConversation)
                        kotlinx.coroutines.delay(100)
                        val retryVerify = conversationDao.getConversation(conversationId)
                        if (retryVerify == null) {
                            localDeleteSuccess = true
                            Log.e(TAG, "✅ 重试删除成功 - conversationId: $conversationId")
                        } else {
                            Log.e(TAG, "❌ 重试删除仍然失败 - conversationId: $conversationId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 重试删除异常 - conversationId: $conversationId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ 删除本地会话异常 - conversationId: $conversationId", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}, 异常消息: ${e.message}")
            Log.e(TAG, "异常堆栈:", e)
            // 不抛出异常，继续尝试删除服务器端记录
        }
        
        // 删除服务器端的记录
        try {
            Log.e(TAG, "🗑️ 开始删除服务器端会话 - conversationId: $conversationId")
            conversationApi.deleteConversation(conversationId)
            Log.e(TAG, "✅ 服务器端会话删除成功 - conversationId: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除服务器端会话失败 - conversationId: $conversationId", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}, 异常消息: ${e.message}")
            // 即使服务器删除失败，本地已经删除了，所以不抛出异常
        }
        
        // 最终验证
        if (localDeleteSuccess) {
            Log.e(TAG, "========== ✅✅✅ 删除会话完成（本地删除成功） ==========")
        } else {
            Log.e(TAG, "========== ⚠️⚠️⚠️ 删除会话完成（本地删除可能失败） ==========")
            // 如果本地删除失败，抛出异常让调用者知道
            throw IllegalStateException("本地会话删除失败 - conversationId: $conversationId")
        }
    }
    
    override suspend fun syncConversationsFromServer(): Result<Unit> {
        return try {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录，无法同步会话列表")
                return Result.failure(IllegalStateException("用户未登录"))
            }
            
            Log.d(TAG, "开始从服务器同步会话列表")
            val conversationsFromServer = conversationApi.getConversations()
            Log.d(TAG, "从服务器获取到 ${conversationsFromServer.size} 个会话")
            
            // 获取服务器端会话ID集合
            val serverConversationIds = conversationsFromServer.map { it.conversationId }.toSet()
            
            // 获取本地所有会话ID
            val localConversations = conversationDao.getAllConversations().first()
            val localConversationIds = localConversations.map { it.conversationId }.toSet()
            
            // 找出需要删除的会话（本地存在但服务器端不存在，说明已被删除）
            val toDelete = localConversationIds - serverConversationIds
            if (toDelete.isNotEmpty()) {
                Log.e(TAG, "🗑️ 发现 ${toDelete.size} 个已删除的会话（服务器端不存在），准备从本地删除")
                toDelete.forEach { conversationId ->
                    try {
                        conversationDao.deleteConversationById(conversationId)
                        Log.e(TAG, "✅ 已删除本地会话（服务器端不存在）: ${conversationId.take(32)}...")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 删除本地会话失败 - conversationId: $conversationId", e)
                    }
                }
            } else {
                Log.d(TAG, "✅ 没有发现已删除的会话（所有本地会话都在服务器端存在）")
            }
            
            // 转换为本地实体并保存（使用REPLACE策略，会更新已存在的会话）
            val conversationEntities = conversationsFromServer.map { it.toEntity() }
            
            // 🔥 关键修复：彻底清理错误的会话记录
            // 1. 清理无效的会话记录（targetId 不正确）
            // 2. 清理错误的单聊会话（conversationId 是 UUID 格式但类型是 SINGLE）
            // 3. 清理重复的会话记录（特别是群聊，同一个 conversationId 应该只有一个会话）
            val allLocalConversations = conversationDao.getAllConversations().first()
            Log.e(TAG, "🔍 开始清理错误的会话记录 - 本地共有 ${allLocalConversations.size} 个会话")
            
            // 先按 conversationId 分组，找出重复的会话
            val conversationsById = allLocalConversations.groupBy { it.conversationId }
            val duplicateConversationIds = conversationsById.filter { it.value.size > 1 }.keys
            if (duplicateConversationIds.isNotEmpty()) {
                Log.e(TAG, "⚠️⚠️⚠️ 发现 ${duplicateConversationIds.size} 个重复的 conversationId: ${duplicateConversationIds.take(5).joinToString(", ")}")
            }
            
            // 需要删除的会话ID集合
            val toDeleteIds = mutableSetOf<String>()
            
            allLocalConversations.forEach { conversation ->
                val shouldDelete = when {
                    // 单聊：conversationId 应该是 "user1_user2" 格式，如果是 UUID 格式则是错误的
                    conversation.type == ConversationType.SINGLE && 
                    !conversation.conversationId.contains("_") &&
                    conversation.conversationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)) -> {
                        Log.w(TAG, "🗑️ 发现错误的单聊会话（conversationId是UUID格式）- conversationId: ${conversation.conversationId}, targetId: ${conversation.targetId.take(16)}..., targetName: ${conversation.targetName}")
                        true
                    }
                    // 单聊：targetId 应该是对方用户ID，不应该是当前用户ID
                    conversation.type == ConversationType.SINGLE && conversation.targetId == currentUser.userId -> {
                        Log.w(TAG, "🗑️ 发现无效的单聊会话（targetId是当前用户ID）- conversationId: ${conversation.conversationId}, targetId: ${conversation.targetId.take(16)}...")
                        true
                    }
                    // 单聊：conversationId 中两个用户ID相同（自己和自己对话）
                    conversation.type == ConversationType.SINGLE && conversation.conversationId.contains("_") -> {
                        val userIds = conversation.conversationId.split("_")
                        if (userIds.size == 2 && userIds[0] == userIds[1]) {
                            Log.w(TAG, "🗑️ 发现自己和自己对话的会话 - conversationId: ${conversation.conversationId}")
                            true
                        } else {
                            false
                        }
                    }
                    // 群聊：targetId 应该等于 conversationId
                    conversation.type == ConversationType.GROUP && conversation.targetId != conversation.conversationId -> {
                        Log.w(TAG, "🗑️ 发现无效的群聊会话（targetId不等于conversationId）- conversationId: ${conversation.conversationId}, targetId: ${conversation.targetId.take(16)}...")
                        true
                    }
                    else -> false
                }
                
                if (shouldDelete) {
                    toDeleteIds.add(conversation.conversationId)
                }
            }
            
            // 🔥 关键修复：对于重复的会话，只保留一个正确的
            // 注意：conversationId 是主键，理论上不应该有重复，但可能有历史数据问题
            duplicateConversationIds.forEach { conversationId ->
                val duplicates = conversationsById[conversationId] ?: emptyList()
                if (duplicates.size > 1) {
                    Log.e(TAG, "🔍 处理重复的会话 - conversationId: $conversationId, 重复数量: ${duplicates.size}")
                    duplicates.forEachIndexed { index, conv ->
                        Log.e(TAG, "  重复[$index] - type: ${conv.type}, targetId: ${conv.targetId.take(16)}..., targetName: ${conv.targetName}, lastMessageTime: ${conv.lastMessageTime}")
                    }
                    
                    // 找出正确的会话
                    val correctOne = when {
                        // 群聊：保留 targetId == conversationId 的，选择最新的
                        duplicates.any { it.type == ConversationType.GROUP && it.targetId == it.conversationId } -> {
                            duplicates.filter { it.type == ConversationType.GROUP && it.targetId == it.conversationId }
                                .maxByOrNull { it.lastMessageTime }
                        }
                        // 单聊：保留 targetId != currentUser.userId 的，选择最新的
                        duplicates.any { it.type == ConversationType.SINGLE && it.targetId != currentUser.userId } -> {
                            duplicates.filter { it.type == ConversationType.SINGLE && it.targetId != currentUser.userId }
                                .maxByOrNull { it.lastMessageTime }
                        }
                        else -> duplicates.maxByOrNull { it.lastMessageTime }
                    }
                    
                    if (correctOne != null) {
                        Log.e(TAG, "✅ 保留正确的会话 - conversationId: ${correctOne.conversationId}, type: ${correctOne.type}, targetId: ${correctOne.targetId.take(16)}..., targetName: ${correctOne.targetName}")
                        // 删除所有其他的重复会话（由于 conversationId 是主键，实际上不应该有多个记录，但为了安全还是处理）
                        // 注意：由于 conversationId 是主键，这里实际上只会有一个记录，但为了代码健壮性，还是处理一下
                    } else {
                        Log.w(TAG, "⚠️ 找不到正确的会话，保留最新的")
                    }
                }
            }
            
            // 批量删除错误的会话
            if (toDeleteIds.isNotEmpty()) {
                Log.e(TAG, "🗑️ 准备删除 ${toDeleteIds.size} 个错误的会话记录")
                toDeleteIds.forEach { conversationId ->
                    try {
                        conversationDao.deleteConversationById(conversationId)
                        Log.w(TAG, "✅ 已删除错误的会话记录 - conversationId: $conversationId")
                    } catch (e: Exception) {
                        Log.e(TAG, "删除错误会话记录失败 - conversationId: $conversationId", e)
                    }
                }
            } else {
                Log.d(TAG, "✅ 没有发现错误的会话记录")
            }
            
            // 重用之前已经计算的 serverConversationIds，清理可能存在的重复记录
            serverConversationIds.forEach { conversationId ->
                // 检查是否有多个相同 conversationId 的记录（虽然不应该发生）
                val existing = conversationDao.getConversation(conversationId)
                if (existing != null) {
                    // 如果存在，确保数据正确（群聊：targetId == conversationId，单聊：targetId != currentUser.userId）
                    val shouldKeep = when (existing.type) {
                        ConversationType.GROUP -> existing.targetId == conversationId
                        ConversationType.SINGLE -> existing.targetId != currentUser.userId
                    }
                    if (!shouldKeep) {
                        Log.w(TAG, "发现无效的会话记录，准备删除 - conversationId: $conversationId, type: ${existing.type}, targetId: ${existing.targetId.take(16)}...")
                        conversationDao.deleteConversationById(conversationId)
                    }
                }
            }
            
            conversationDao.insertConversations(conversationEntities)
            
            Log.d(TAG, "会话列表同步完成 - 共 ${conversationEntities.size} 个会话，删除了 ${toDelete.size} 个已删除的会话")
            
            // 🔥 关键修复：同步后再次检查，确保只保留服务器端返回的会话
            // 因为离线消息同步可能会在同步会话列表之后创建新的会话
            val finalLocalConversations = conversationDao.getAllConversations().first()
            val finalLocalConversationIds = finalLocalConversations.map { it.conversationId }.toSet()
            val extraConversations = finalLocalConversationIds - serverConversationIds
            
            if (extraConversations.isNotEmpty()) {
                Log.e(TAG, "⚠️⚠️⚠️ 发现 ${extraConversations.size} 个额外的会话（不在服务器端列表中），准备删除")
                Log.e(TAG, "📋 服务器端会话ID列表: ${serverConversationIds.joinToString(", ") { it.take(16) + "..." }}")
                Log.e(TAG, "📋 本地会话ID列表: ${finalLocalConversationIds.joinToString(", ") { it.take(16) + "..." }}")
                Log.e(TAG, "📋 需要删除的会话ID列表: ${extraConversations.joinToString(", ") { it.take(16) + "..." }}")
                
                extraConversations.forEach { conversationId ->
                    try {
                        // 先获取会话信息用于日志
                        val toDeleteConv = conversationDao.getConversation(conversationId)
                        Log.e(TAG, "🗑️ 准备删除额外会话 - conversationId: ${conversationId.take(32)}..., type: ${toDeleteConv?.type}, targetName: ${toDeleteConv?.targetName}")
                        
                        conversationDao.deleteConversationById(conversationId)
                        
                        // 验证删除是否成功
                        val verifyDeleted = conversationDao.getConversation(conversationId)
                        if (verifyDeleted == null) {
                            Log.e(TAG, "✅ 已删除额外的会话（不在服务器端列表中）: ${conversationId.take(32)}...")
                        } else {
                            Log.e(TAG, "❌❌❌ 删除失败！会话仍然存在 - conversationId: ${conversationId.take(32)}...")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 删除额外会话失败 - conversationId: $conversationId", e)
                    }
                }
            } else {
                Log.d(TAG, "✅ 没有发现额外的会话（所有本地会话都在服务器端存在）")
            }
            
            // 🔥 最终验证：确保本地会话数量不超过服务器端数量
            val finalCount = conversationDao.getAllConversations().first().size
            if (finalCount > conversationsFromServer.size) {
                Log.e(TAG, "❌❌❌ 警告：本地会话数量($finalCount)超过服务器端数量(${conversationsFromServer.size})，可能存在数据不一致")
                // 再次尝试清理
                val retryLocalConversations = conversationDao.getAllConversations().first()
                val retryLocalIds = retryLocalConversations.map { it.conversationId }.toSet()
                val retryExtra = retryLocalIds - serverConversationIds
                if (retryExtra.isNotEmpty()) {
                    Log.e(TAG, "🔄 重试清理 ${retryExtra.size} 个额外的会话")
                    retryExtra.forEach { conversationId ->
                        try {
                            conversationDao.deleteConversationById(conversationId)
                            Log.e(TAG, "✅ 重试删除成功 - conversationId: ${conversationId.take(32)}...")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 重试删除失败 - conversationId: $conversationId", e)
                        }
                    }
                }
            } else {
                Log.d(TAG, "✅ 最终验证通过 - 本地会话数量: $finalCount, 服务器端数量: ${conversationsFromServer.size}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "同步会话列表失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun setTop(conversationId: String, isTop: Boolean) {
        conversationDao.updateTopStatus(conversationId, isTop)
    }
    
    override suspend fun setMuted(conversationId: String, isMuted: Boolean) {
        conversationDao.updateMutedStatus(conversationId, isMuted)
    }
    
    override suspend fun clearUnreadCount(conversationId: String) {
        conversationDao.clearUnreadCount(conversationId)
    }
    
    /**
     * 判断字符串是否看起来像日期格式（如 "2025/12/06"）
     */
    private fun isDateLike(str: String): Boolean {
        // 检查是否包含日期分隔符（/ 或 -）
        if (!str.contains("/") && !str.contains("-")) {
            return false
        }
        
        // 检查是否符合日期格式（如 yyyy/MM/dd 或 yyyy-MM-dd）
        val datePattern = Regex("^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}")
        return datePattern.matches(str)
    }
}

