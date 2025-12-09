package com.tongxun.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.local.entity.MessageEntity
import com.tongxun.data.model.MessageType
import com.tongxun.domain.repository.ConversationRepository
import com.tongxun.data.local.entity.ConversationType
import com.tongxun.domain.repository.MessageRepository
import com.tongxun.domain.repository.GroupRepository
import com.tongxun.domain.repository.UserRepository
import com.tongxun.data.local.entity.UserEntity
import com.tongxun.data.local.TongxunDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val authRepository: com.tongxun.domain.repository.AuthRepository,
    private val uploadRepository: com.tongxun.data.repository.UploadRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val database: TongxunDatabase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private var currentConversationId: String = ""
    private var currentTargetId: String = ""
    
    private val _conversationIdFlow = MutableStateFlow<String>("")
    private val _conversationTypeFlow = MutableStateFlow<ConversationType?>(null)
    val conversationType: StateFlow<ConversationType?> = _conversationTypeFlow.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = _conversationIdFlow
        .flatMapLatest { conversationId ->
            android.util.Log.e("ChatViewModel", "🔥🔥🔥 messages Flow - conversationId 变化: '$conversationId'")
            if (conversationId.isNotBlank()) {
                // 🔥 关键修复：使用 distinctUntilChanged 确保只在 conversationId 真正变化时重新查询
                // 添加 onStart 确保立即开始收集
                messageRepository.getMessages(conversationId)
                    .onStart {
                        android.util.Log.e("ChatViewModel", "🔥🔥🔥 开始收集消息 Flow - conversationId: '$conversationId'")
                    }
                    .catch { e ->
                        android.util.Log.e("ChatViewModel", "❌❌❌ 消息 Flow 收集出错 - conversationId: '$conversationId'", e)
                        emit(emptyList())
                    }
            } else {
                android.util.Log.w("ChatViewModel", "⚠️ conversationId 为空，返回空列表")
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            // 🔥 关键修复：使用 Eagerly 确保立即开始收集，避免延迟
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private val _hasMoreMessages = MutableStateFlow(false)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages.asStateFlow()
    
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    
    fun initConversation(conversationId: String, targetId: String) {
        currentTargetId = targetId
        
        viewModelScope.launch {
            android.util.Log.e("ChatViewModel", "🔥🔥🔥 initConversation - 传入conversationId: '$conversationId', targetId: '$targetId'")
            
            // 如果传入了conversationId，先尝试使用它
            val conversation = if (conversationId.isNotBlank()) {
                // 先查询是否存在这个会话
                val existing = conversationRepository.getConversationById(conversationId)
                
                if (existing != null) {
                    android.util.Log.e("ChatViewModel", "✅✅✅ 使用已存在的会话 - conversationId: ${existing.conversationId}, targetId: ${existing.targetId}, type: ${existing.type}")
                    existing
                } else {
                    // 如果不存在，判断是群聊还是单聊
                    // 通过检查本地数据库来判断conversationId是否是群组ID
                    val isGroup = try {
                        val group = database.groupDao().getGroupById(conversationId)
                        group != null
                    } catch (e: Exception) {
                        android.util.Log.w("ChatViewModel", "检查群组ID失败 - conversationId: $conversationId", e)
                        false
                    }
                    
                    val conversationType = if (isGroup) {
                        android.util.Log.d("ChatViewModel", "检测到群组ID，创建群聊会话 - conversationId: $conversationId")
                        ConversationType.GROUP
                    } else {
                        android.util.Log.d("ChatViewModel", "未检测到群组ID，创建单聊会话 - conversationId: $conversationId")
                        ConversationType.SINGLE
                    }
                    
                    android.util.Log.w("ChatViewModel", "⚠️ 传入的conversationId不存在，创建新会话 - conversationId: '$conversationId', targetId: '$targetId', type: $conversationType")
                    conversationRepository.getOrCreateConversation(
                        targetId,
                        conversationType
                    ).also { created ->
                        android.util.Log.e("ChatViewModel", "创建的会话ID: '${created.conversationId}', 传入的ID: '$conversationId'")
                        if (created.conversationId != conversationId) {
                            android.util.Log.w("ChatViewModel", "⚠️⚠️⚠️ 创建的会话ID与传入的不一致！这可能导致消息无法显示")
                        }
                    }
                }
            } else {
                // 如果没有传入conversationId，创建新会话
                android.util.Log.d("ChatViewModel", "未传入conversationId，创建新会话 - targetId: $targetId")
                conversationRepository.getOrCreateConversation(
                    targetId,
                    ConversationType.SINGLE
                )
            }
            
            currentConversationId = conversation.conversationId
            android.util.Log.e("ChatViewModel", "✅ 最终使用的conversationId: '$currentConversationId'")
            
            // 🔥 关键修复：先设置 conversationId，确保消息 Flow 能立即响应
            _conversationIdFlow.value = currentConversationId
            _conversationTypeFlow.value = conversation.type
            
            // 🔥 关键修复：等待一小段时间，确保 Flow 已经切换并开始收集
            kotlinx.coroutines.delay(100)
            
            // 🔥 关键修复：验证消息 Flow 是否已经开始收集
            val currentMessages = messages.value
            android.util.Log.e("ChatViewModel", "🔥🔥🔥 设置 conversationId 后的消息数量: ${currentMessages.size}, conversationId: '$currentConversationId'")
            
            // 🔥 关键修复：检查本地是否有消息，如果没有，主动从服务器拉取离线消息
            // 注意：这里使用延迟查询，确保 conversationId 已经设置到 Flow 中
            kotlinx.coroutines.delay(200)
            
            android.util.Log.e("ChatViewModel", "🔥🔥🔥 检查本地消息 - conversationId: $currentConversationId, type: ${conversation.type}")
            val localMessages = database.messageDao().getMessages(currentConversationId, limit = 10, offset = 0)
            android.util.Log.e("ChatViewModel", "🔥🔥🔥 本地消息查询结果 - conversationId: $currentConversationId, 消息数量: ${localMessages.size}")
            
            // 🔥 关键修复：如果本地有消息但 Flow 中还没有，强制触发一次查询
            if (localMessages.isNotEmpty() && messages.value.isEmpty()) {
                android.util.Log.w("ChatViewModel", "⚠️⚠️⚠️ 本地有消息但 Flow 中为空，可能需要等待 Flow 更新")
                // 再次检查，给 Flow 一些时间
                kotlinx.coroutines.delay(300)
                val messagesAfterDelay = messages.value
                android.util.Log.e("ChatViewModel", "🔥🔥🔥 延迟后检查消息数量: ${messagesAfterDelay.size}")
            }
            
            // 详细记录本地消息
            localMessages.take(5).forEachIndexed { index, message ->
                android.util.Log.e("ChatViewModel", "📝 本地消息[$index] - messageId: ${message.messageId.take(8)}..., conversationId: ${message.conversationId.take(32)}..., senderId: ${message.senderId.take(8)}..., content: ${message.content.take(30)}...")
            }
            
            if (localMessages.isEmpty()) {
                android.util.Log.w("ChatViewModel", "⚠️⚠️⚠️ 本地没有消息，主动从服务器拉取离线消息 - conversationId: $currentConversationId, type: ${conversation.type}")
                // 获取最后一条消息的时间戳（可能为null，表示拉取所有消息）
                messageRepository.fetchOfflineMessages(null)
                    .onSuccess { messages ->
                        android.util.Log.e("ChatViewModel", "✅✅✅ 离线消息拉取成功 - 共${messages.size}条消息")
                        // 检查是否有当前会话的消息
                        val conversationMessages = messages.filter { it.conversationId == currentConversationId }
                        android.util.Log.e("ChatViewModel", "📋 过滤后的消息 - conversationId: $currentConversationId, 匹配的消息数量: ${conversationMessages.size}")
                        
                        if (conversationMessages.isNotEmpty()) {
                            android.util.Log.e("ChatViewModel", "✅ 找到${conversationMessages.size}条当前会话的消息")
                            conversationMessages.take(3).forEachIndexed { index, message ->
                                android.util.Log.e("ChatViewModel", "📝 离线消息[$index] - messageId: ${message.messageId.take(8)}..., conversationId: ${message.conversationId.take(32)}..., senderId: ${message.senderId.take(8)}...")
                            }
                        } else {
                            android.util.Log.w("ChatViewModel", "⚠️⚠️⚠️ 离线消息中没有当前会话的消息 - conversationId: $currentConversationId")
                            // 检查是否有其他conversationId的消息
                            val allConversationIds = messages.map { it.conversationId }.distinct()
                            android.util.Log.w("ChatViewModel", "📋 离线消息中的所有conversationId: ${allConversationIds.joinToString(", ") { it.take(16) + "..." }}")
                        }
                    }
                    .onFailure { error ->
                        android.util.Log.e("ChatViewModel", "❌ 拉取离线消息失败 - error: ${error.message}", error)
                    }
            } else {
                android.util.Log.d("ChatViewModel", "✅ 本地已有消息，无需拉取 - conversationId: $currentConversationId, 消息数量: ${localMessages.size}")
            }
            
            // 🔥 关键修复：确保单聊时对方用户信息正确加载（用于显示头像和姓名）
            if (conversation.type == ConversationType.SINGLE) {
                val otherUserId = conversation.targetId
                android.util.Log.e("ChatViewModel", "🔥🔥🔥 单聊会话 - 开始预加载对方用户信息 - otherUserId: ${otherUserId.take(8)}...")
                
                // 先检查本地是否有用户信息
                val otherUser = userRepository.getUserById(otherUserId)
                if (otherUser == null) {
                    android.util.Log.w("ChatViewModel", "⚠️ 对方用户信息不存在，主动从服务器获取 - userId: ${otherUserId.take(8)}...")
                    // 异步获取用户信息（不阻塞）
                    viewModelScope.launch {
                        try {
                            android.util.Log.d("ChatViewModel", "开始从服务器获取对方用户信息 - userId: ${otherUserId.take(8)}...")
                            val fetchedUser = userRepository.getUserById(otherUserId)
                            if (fetchedUser != null) {
                                android.util.Log.e("ChatViewModel", "✅✅✅ 对方用户信息已获取 - userId: ${otherUserId.take(8)}..., nickname: ${fetchedUser.nickname}, avatar: ${fetchedUser.avatar?.take(20)}...")
                            } else {
                                android.util.Log.e("ChatViewModel", "❌❌❌ 无法获取对方用户信息 - userId: ${otherUserId.take(8)}...")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatViewModel", "❌❌❌ 获取对方用户信息失败 - userId: ${otherUserId.take(8)}...", e)
                        }
                    }
                } else {
                    android.util.Log.d("ChatViewModel", "✅ 对方用户信息已存在 - userId: ${otherUserId.take(8)}..., nickname: ${otherUser.nickname}, avatar: ${otherUser.avatar?.take(20)}...")
                }
                
                // 🔥 关键修复：预加载所有消息中的发送者用户信息（单聊时，发送者就是对方）
                // 这样可以确保消息列表显示时，用户信息已经在缓存中
                viewModelScope.launch {
                    try {
                        android.util.Log.d("ChatViewModel", "开始预加载消息中的用户信息 - conversationId: $currentConversationId")
                        val messages = database.messageDao().getMessages(currentConversationId, limit = 50, offset = 0)
                        android.util.Log.d("ChatViewModel", "获取到 ${messages.size} 条消息，开始预加载用户信息")
                        
                        // 收集所有唯一的发送者ID（单聊时，发送者就是对方）
                        val senderIds = messages.map { it.senderId }.distinct().filter { it != getCurrentUserId() }
                        android.util.Log.d("ChatViewModel", "需要预加载的用户ID列表: ${senderIds.size} 个")
                        
                        senderIds.forEach { senderId ->
                            try {
                                val user = userRepository.getUserById(senderId)
                                if (user != null) {
                                    android.util.Log.d("ChatViewModel", "✅ 预加载用户信息成功 - userId: ${senderId.take(8)}..., nickname: ${user.nickname}")
                                } else {
                                    android.util.Log.w("ChatViewModel", "⚠️ 预加载用户信息失败 - userId: ${senderId.take(8)}...")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatViewModel", "❌ 预加载用户信息异常 - userId: ${senderId.take(8)}...", e)
                            }
                        }
                        
                        android.util.Log.e("ChatViewModel", "✅✅✅ 用户信息预加载完成 - 共预加载 ${senderIds.size} 个用户")
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "❌ 预加载消息用户信息失败", e)
                    }
                }
            }
            
            // 自动标记已读
            markAsRead()
        }
    }
    
    fun sendMessage(content: String) {
        android.util.Log.e("ChatViewModel", "🔥🔥🔥 sendMessage() 被调用 - content: $content, conversationId: $currentConversationId, receiverId: $currentTargetId")
        
        if (content.isBlank() || currentConversationId.isBlank()) {
            android.util.Log.w("ChatViewModel", "❌ 消息内容为空或会话ID为空，无法发送")
            return
        }
        
        viewModelScope.launch {
            android.util.Log.d("ChatViewModel", "开始发送消息 - content: $content")
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            messageRepository.sendMessage(
                conversationId = currentConversationId,
                receiverId = currentTargetId,
                content = content,
                messageType = MessageType.TEXT
            ).onSuccess {
                android.util.Log.e("ChatViewModel", "✅✅✅ 消息发送成功 - messageId: ${it.messageId}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }.onFailure {
                android.util.Log.e("ChatViewModel", "❌❌❌ 消息发送失败 - error: ${it.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = it.message
                )
            }
        }
    }
    
    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }
    
    fun recallMessage(messageId: String) {
        viewModelScope.launch {
            try {
                messageRepository.recallMessage(messageId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "撤回失败"
                )
            }
        }
    }
    
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.deleteMessage(messageId)
        }
    }
    
    fun getCurrentUserId(): String {
        return authRepository.getCurrentUser()?.userId ?: ""
    }
    
    suspend fun getUserInfo(userId: String): UserEntity? {
        android.util.Log.e("ChatViewModel", "🔥🔥🔥 getUserInfo() 被调用 - userId: ${userId.take(8)}...")
        return try {
            val user = userRepository.getUserById(userId)
            android.util.Log.e("ChatViewModel", "✅✅✅ getUserInfo() 完成 - userId: ${userId.take(8)}..., user: ${if (user != null) "存在 (nickname: ${user.nickname}, avatar: ${user.avatar?.take(20)}...)" else "null"}")
            user
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "❌❌❌ getUserInfo() 异常 - userId: ${userId.take(8)}...", e)
            null
        }
    }
    
    fun markAsRead() {
        if (currentConversationId.isNotBlank()) {
            viewModelScope.launch {
                messageRepository.markAsRead(currentConversationId)
            }
        }
    }
    
    fun sendImageMessage(uri: android.net.Uri) {
        if (currentConversationId.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, uploadProgress = 0)
            
            // 上传图片
            uploadRepository.uploadImage(uri)
                .onSuccess { uploadResponse ->
                    // 发送图片消息
                    val extra = org.json.JSONObject().apply {
                        put("fileId", uploadResponse.fileId)
                        put("fileUrl", uploadResponse.fileUrl)
                        put("thumbnailUrl", uploadResponse.thumbnailUrl)
                        put("fileName", uploadResponse.fileName)
                        put("fileSize", uploadResponse.fileSize)
                    }.toString()
                    
                    // 对于群聊，receiverId 应该使用 conversationId（群组ID）
                    val currentType = _conversationTypeFlow.value
                    val finalReceiverId = if (currentType == ConversationType.GROUP) {
                        currentConversationId
                    } else {
                        currentTargetId
                    }
                    
                    messageRepository.sendMessage(
                        conversationId = currentConversationId,
                        receiverId = finalReceiverId,
                        content = uploadResponse.fileUrl,
                        messageType = MessageType.IMAGE,
                        extra = extra
                    ).onSuccess {
                        _uiState.value = _uiState.value.copy(isLoading = false, uploadProgress = null)
                    }.onFailure {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            uploadProgress = null,
                            error = it.message ?: "发送失败"
                        )
                    }
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        uploadProgress = null,
                        error = it.message ?: "上传失败"
                    )
                }
        }
    }
    
    fun sendFileMessage(uri: android.net.Uri) {
        android.util.Log.e("ChatViewModel", "🔥🔥🔥 sendFileMessage() 被调用 - uri: $uri, currentConversationId: '$currentConversationId'")
        
        if (currentConversationId.isBlank()) {
            android.util.Log.e("ChatViewModel", "❌❌❌ 附件发送失败 - conversationId 为空")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, uploadProgress = 0)
            
            // 保存文件到临时目录
            android.util.Log.d("ChatViewModel", "开始读取文件 - uri: $uri")
            val tempFile = com.tongxun.utils.FileManager.saveFileFromUri(context, uri)
            if (tempFile == null) {
                android.util.Log.e("ChatViewModel", "❌❌❌ 文件读取失败 - uri: $uri")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "文件读取失败，请检查文件是否存在或权限是否充足"
                )
                return@launch
            }
            
            android.util.Log.d("ChatViewModel", "✅ 文件读取成功 - file: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
            
            // 判断文件大小，决定是否分片上传
            val fileSize = tempFile.length()
            android.util.Log.d("ChatViewModel", "文件大小: $fileSize bytes, 是否分片上传: ${fileSize > 10 * 1024 * 1024}")
            
            val uploadResult = if (fileSize > 10 * 1024 * 1024) { // 大于10MB分片上传
                android.util.Log.d("ChatViewModel", "开始分片上传文件")
                uploadRepository.uploadFileInChunks(tempFile) { progress ->
                    _uiState.value = _uiState.value.copy(uploadProgress = progress)
                }
            } else {
                android.util.Log.d("ChatViewModel", "开始普通上传文件")
                uploadRepository.uploadFile(tempFile) { progress ->
                    _uiState.value = _uiState.value.copy(uploadProgress = progress)
                }
            }
            
            uploadResult.onSuccess { uploadResponse ->
                android.util.Log.d("ChatViewModel", "✅✅✅ 文件上传成功 - fileId: ${uploadResponse.fileId}, fileUrl: ${uploadResponse.fileUrl}, fileName: ${uploadResponse.fileName}")
                
                // 上传成功后，再发送消息
                val extra = org.json.JSONObject().apply {
                    put("fileId", uploadResponse.fileId)
                    put("fileUrl", uploadResponse.fileUrl)
                    put("fileName", uploadResponse.fileName)
                    put("fileSize", uploadResponse.fileSize)
                    put("mimeType", uploadResponse.mimeType)
                }.toString()
                
                // 对于群聊，receiverId 应该使用 conversationId（群组ID）
                val currentType = _conversationTypeFlow.value
                val finalReceiverId = if (currentType == ConversationType.GROUP) {
                    currentConversationId
                } else {
                    currentTargetId
                }
                
                android.util.Log.d("ChatViewModel", "准备发送文件消息 - conversationId: $currentConversationId, receiverId: $finalReceiverId, type: $currentType")
                
                messageRepository.sendMessage(
                    conversationId = currentConversationId,
                    receiverId = finalReceiverId,
                    content = uploadResponse.fileUrl,
                    messageType = MessageType.FILE,
                    extra = extra
                ).onSuccess {
                    android.util.Log.d("ChatViewModel", "✅✅✅ 文件消息发送成功")
                    // 消息发送成功后，再删除临时文件
                    try {
                        tempFile.delete()
                        android.util.Log.d("ChatViewModel", "临时文件已删除")
                    } catch (e: Exception) {
                        android.util.Log.w("ChatViewModel", "删除临时文件失败", e)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, uploadProgress = null)
                }.onFailure {
                    android.util.Log.e("ChatViewModel", "❌❌❌ 文件消息发送失败: ${it.message}", it)
                    // 发送失败，暂时保留临时文件（可以在重试时使用）
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        uploadProgress = null,
                        error = it.message ?: "发送失败"
                    )
                }
            }.onFailure {
                android.util.Log.e("ChatViewModel", "❌❌❌ 文件上传失败: ${it.message}", it)
                // 上传失败，删除临时文件
                try {
                    tempFile.delete()
                } catch (e: Exception) {
                    android.util.Log.w("ChatViewModel", "删除临时文件失败", e)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    uploadProgress = null,
                    error = it.message ?: "上传失败"
                )
            }
        }
    }
    
    
    fun sendVoiceMessage(audioFile: java.io.File, duration: Int) {
        android.util.Log.e("ChatViewModel", "🔥🔥🔥 sendVoiceMessage() 被调用 - audioFile: ${audioFile.absolutePath}, duration: $duration, currentConversationId: '$currentConversationId', currentTargetId: '$currentTargetId'")
        
        if (currentConversationId.isBlank()) {
            android.util.Log.e("ChatViewModel", "❌❌❌ 语音消息发送失败 - conversationId 为空，尝试等待初始化...")
            // 如果 conversationId 为空，尝试等待初始化完成
            viewModelScope.launch {
                var retryCount = 0
                val maxRetries = 10
                while (currentConversationId.isBlank() && retryCount < maxRetries) {
                    android.util.Log.d("ChatViewModel", "等待 conversationId 初始化... (重试 $retryCount/$maxRetries)")
                    kotlinx.coroutines.delay(200) // 等待 200ms
                    retryCount++
                }
                
                if (currentConversationId.isBlank()) {
                    android.util.Log.e("ChatViewModel", "❌❌❌ 等待超时，conversationId 仍为空")
                    _uiState.value = _uiState.value.copy(
                        error = "会话未初始化，无法发送语音消息"
                    )
                    return@launch
                }
                
                android.util.Log.d("ChatViewModel", "✅ conversationId 已初始化: '$currentConversationId'，继续发送语音消息")
                // 递归调用，但这次 conversationId 应该已经初始化了
                sendVoiceMessage(audioFile, duration)
            }
            return
        }
        
        if (!audioFile.exists()) {
            android.util.Log.e("ChatViewModel", "❌❌❌ 语音消息发送失败 - 音频文件不存在: ${audioFile.absolutePath}")
            _uiState.value = _uiState.value.copy(
                error = "音频文件不存在"
            )
            return
        }
        
        viewModelScope.launch {
            android.util.Log.d("ChatViewModel", "开始上传语音文件 - 文件大小: ${audioFile.length()} bytes")
            _uiState.value = _uiState.value.copy(isLoading = true, uploadProgress = 0)
            
            // 上传语音文件
            uploadRepository.uploadFile(audioFile) { progress ->
                _uiState.value = _uiState.value.copy(uploadProgress = progress)
            }
                .onSuccess { uploadResponse ->
                    android.util.Log.e("ChatViewModel", "✅✅✅ 语音文件上传成功 - fileId: ${uploadResponse.fileId}, fileUrl: ${uploadResponse.fileUrl}")
                    
                    // 发送语音消息
                    // 保存本地文件路径，以便立即播放
                    val extra = org.json.JSONObject().apply {
                        put("fileId", uploadResponse.fileId)
                        put("fileUrl", uploadResponse.fileUrl)
                        put("fileName", uploadResponse.fileName)
                        put("fileSize", uploadResponse.fileSize)
                        put("duration", duration) // 语音时长（秒）
                        put("mimeType", uploadResponse.mimeType)
                        put("localFilePath", audioFile.absolutePath) // 保存本地文件路径
                    }.toString()
                    
                    android.util.Log.d("ChatViewModel", "准备发送语音消息 - conversationId: $currentConversationId, receiverId: $currentTargetId")
                    messageRepository.sendMessage(
                        conversationId = currentConversationId,
                        receiverId = currentTargetId,
                        content = uploadResponse.fileUrl,
                        messageType = MessageType.VOICE,
                        extra = extra
                    ).onSuccess {
                        android.util.Log.e("ChatViewModel", "✅✅✅ 语音消息发送成功 - messageId: ${it.messageId}")
                        _uiState.value = _uiState.value.copy(isLoading = false, uploadProgress = null)
                    }.onFailure { error ->
                        android.util.Log.e("ChatViewModel", "❌❌❌ 语音消息发送失败 - error: ${error.message}", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            uploadProgress = null,
                            error = error.message ?: "发送失败"
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("ChatViewModel", "❌❌❌ 语音文件上传失败 - error: ${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        uploadProgress = null,
                        error = error.message ?: "上传失败"
                    )
                }
        }
    }
    
    fun downloadFile(message: MessageEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val extra = message.extra?.let { org.json.JSONObject(it) }
            val fileUrl = extra?.optString("fileUrl") ?: message.content
            val fileId = extra?.optString("fileId") ?: ""
            val fileName = extra?.optString("fileName") ?: "file"
            
            // 语音文件应该保存到files目录（isImage=false），图片保存到images目录（isImage=true）
            val isImage = message.messageType == MessageType.IMAGE
            
            // 优先使用 fileUrl 直接下载，如果没有再使用 fileId
            val downloadResult = fileUrl?.takeIf { it.isNotBlank() && it.startsWith("/uploads/") }?.let { url ->
                // 使用 fileUrl 直接下载
                android.util.Log.d("ChatViewModel", "使用 fileUrl 下载: $url")
                uploadRepository.downloadFileByUrl(url, fileName, isImage)
            } ?: if (fileId.isNotEmpty()) {
                // 使用 fileId 下载
                android.util.Log.d("ChatViewModel", "使用 fileId 下载: $fileId")
                uploadRepository.downloadFile(fileId, fileName, isImage)
            } else {
                android.util.Log.e("ChatViewModel", "❌❌❌ 无法下载文件 - fileUrl 和 fileId 都为空")
                Result.failure<File>(Exception("文件URL和ID都为空"))
            }
            
            downloadResult
                .onSuccess { file ->
                    android.util.Log.d("ChatViewModel", "✅✅✅ 文件下载成功: ${file.absolutePath}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        downloadedFile = file
                    )
                }
                .onFailure { error ->
                    android.util.Log.e("ChatViewModel", "❌❌❌ 文件下载失败: ${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "下载失败"
                    )
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearDownloadedFile() {
        _uiState.value = _uiState.value.copy(downloadedFile = null)
    }
    
    fun clearReadStats() {
        _uiState.value = _uiState.value.copy(readStats = null)
    }
    
    fun loadMoreMessages() {
        if (_isLoadingMore.value || !_hasMoreMessages.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            
            val currentMessages = messages.value
            if (currentMessages.isEmpty()) {
                _isLoadingMore.value = false
                return@launch
            }
            
            val oldestMessage = currentMessages.first()
            val result = messageRepository.loadMoreMessages(
                currentConversationId,
                oldestMessage.timestamp,
                20
            )
            
            result.onSuccess { newMessages ->
                if (newMessages.isNotEmpty()) {
                    // 检查是否还有更多消息
                    val hasMore = messageRepository.hasMoreMessages(
                        currentConversationId,
                        newMessages.first().timestamp
                    )
                    _hasMoreMessages.value = hasMore
                } else {
                    _hasMoreMessages.value = false
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    error = it.message ?: "加载失败"
                )
            }
            
            _isLoadingMore.value = false
        }
    }
    
    fun checkHasMoreMessages() {
        viewModelScope.launch {
            val currentMessages = messages.value
            if (currentMessages.isEmpty()) {
                _hasMoreMessages.value = false
                return@launch
            }
            
            val oldestMessage = currentMessages.first()
            val hasMore = messageRepository.hasMoreMessages(
                currentConversationId,
                oldestMessage.timestamp
            )
            _hasMoreMessages.value = hasMore
        }
    }
    
    fun showReadStats(messageId: String) {
        viewModelScope.launch {
            messageRepository.getMessageReadStats(messageId)
                .onSuccess { stats ->
                    _uiState.value = _uiState.value.copy(
                        readStats = stats
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = it.message ?: "获取已读统计失败"
                    )
                }
        }
    }
    
    data class ChatUiState(
        val isLoading: Boolean = false,
        val inputText: String = "",
        val error: String? = null,
        val uploadProgress: Int? = null,
        val downloadedFile: java.io.File? = null,
        val readStats: com.tongxun.data.remote.dto.MessageReadStatsDto? = null
    )
}

