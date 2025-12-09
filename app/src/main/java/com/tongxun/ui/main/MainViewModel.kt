package com.tongxun.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.remote.WebSocketManager
import com.tongxun.data.repository.MessageRepositoryImpl
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.domain.repository.FriendRepository
import com.tongxun.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val messageRepositoryImpl: MessageRepositoryImpl,
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val conversationRepository: com.tongxun.domain.repository.ConversationRepository
) : ViewModel() {
    
    private val TAG = "MainViewModel"
    
    // 账号被踢，需要跳转到登录页面
    private val _shouldNavigateToLogin = MutableStateFlow<String?>(null)
    val shouldNavigateToLogin: StateFlow<String?> = _shouldNavigateToLogin.asStateFlow()
    
    // WebSocket Flow收集器的Job，用于取消旧的收集器
    private var websocketListenerJob: kotlinx.coroutines.Job? = null
    
    fun clearNavigateToLogin() {
        _shouldNavigateToLogin.value = null
    }
    
    /**
     * 强制重新连接WebSocket（公开方法，供MainActivity调用）
     */
    fun reconnectWebSocket() {
        Log.e(TAG, "🔥🔥🔥 reconnectWebSocket() 被调用 - 强制重新连接WebSocket")
        // 先取消旧的收集器（如果存在）
        websocketListenerJob?.cancel()
        websocketListenerJob = null
        // 重新设置WebSocket监听器（会触发新的连接）
        setupWebSocketListener()
    }
    
    init {
        Log.e(TAG, "🔥🔥🔥 MainViewModel.init() 被调用 - 代码已更新 🔥🔥🔥")
        Log.d(TAG, "=== MainViewModel.init() 开始 ===")
        Log.d(TAG, "MainViewModel 初始化 - 开始设置WebSocket监听")
        setupWebSocketListener()
        
        // 🔥 关键修复：在应用启动时修复单聊消息的conversationId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.e(TAG, "🔥🔥🔥 开始修复单聊消息的conversationId...")
                val result = messageRepositoryImpl.fixSingleChatMessages()
                result.onSuccess { fixedCount ->
                    if (fixedCount > 0) {
                        Log.e(TAG, "✅✅✅ 已修复 $fixedCount 条单聊消息的conversationId")
                    } else {
                        Log.d(TAG, "✅ 没有发现需要修复的消息")
                    }
                }.onFailure { error ->
                    Log.e(TAG, "❌ 修复单聊消息失败", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 修复单聊消息异常", e)
            }
        }
        
        // 不在 init 中立即拉取离线消息，等待 WebSocket 连接成功后再拉取
        // fetchOfflineMessages() // 已移除，改为在 WebSocket 连接成功时拉取
        Log.d(TAG, "=== MainViewModel.init() 完成 ===")
    }
    
    private fun fetchOfflineMessages() {
        viewModelScope.launch {
            try {
                Log.e(TAG, "🔥🔥🔥 fetchOfflineMessages() 被调用 - 开始拉取离线消息")
                
                // 获取最后一条消息的时间戳
                val lastMessageTime = messageRepositoryImpl.getLastMessageTimestamp()
                Log.e(TAG, "📥 获取最后一条消息时间戳 - lastMessageTime=$lastMessageTime")
                
                Log.e(TAG, "📡 开始调用 API 拉取离线消息 - lastMessageTime=$lastMessageTime")
                messageRepository.fetchOfflineMessages(lastMessageTime)
                    .onSuccess { messages ->
                        Log.e(TAG, "✅✅✅ 离线消息拉取成功 - 共${messages.size}条消息")
                        if (messages.isNotEmpty()) {
                            Log.e(TAG, "📨 第一条消息 - messageId=${messages[0].messageId}, senderId=${messages[0].senderId}, content=${messages[0].content.take(50)}")
                        }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "❌❌❌ 离线消息拉取失败 - error: ${error.message}", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ 拉取离线消息异常", e)
            }
        }
    }
    
    private fun syncConversations() {
        viewModelScope.launch {
            try {
                Log.e(TAG, "🔥🔥🔥 syncConversations() 被调用 - 开始同步会话列表")
                conversationRepository.syncConversationsFromServer()
                    .onSuccess {
                        Log.e(TAG, "✅✅✅ 会话列表同步成功")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "❌❌❌ 会话列表同步失败 - error: ${error.message}", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ 同步会话列表异常", e)
            }
        }
    }
    
    private fun setupWebSocketListener() {
        Log.e(TAG, "=== MainViewModel.setupWebSocketListener() 开始 ===")
        Log.e(TAG, "开始监听WebSocket连接状态")
        
        // 检查WebSocket是否已初始化
        try {
            val isConnected = webSocketManager.isConnected()
            Log.e(TAG, "WebSocket连接状态检查 - isConnected: $isConnected")
        } catch (e: Exception) {
            Log.e(TAG, "检查WebSocket状态失败", e)
        }
        
        try {
            // 先取消旧的收集器（如果存在）
            websocketListenerJob?.cancel()
            websocketListenerJob = null
            Log.e(TAG, "🔥🔥🔥 准备调用 webSocketManager.connect()")
            
            // 创建新的收集器并保存Job
            websocketListenerJob = viewModelScope.launch {
                webSocketManager.connect()
                    .collect { state ->
                        Log.e(TAG, "收到WebSocket状态变化: $state")
                    when (state) {
                    is WebSocketManager.ConnectionState.Connected -> {
                        Log.e(TAG, "✅✅✅ WebSocket已连接，准备拉取离线消息和同步会话列表")
                        // WebSocket连接成功后，延迟500ms再拉取离线消息和同步会话列表，确保连接完全建立
                        viewModelScope.launch {
                            delay(500)
                            Log.e(TAG, "⏰ 延迟完成，开始拉取离线消息和同步会话列表")
                            // 🔥 关键修复：先同步会话列表，再拉取离线消息，最后再次同步会话列表
                            // 确保离线消息创建的新会话被正确清理
                            syncConversations()
                            fetchOfflineMessages()
                            // 离线消息同步完成后，再次同步会话列表，清理可能创建的错误会话
                            delay(1000) // 等待离线消息同步完成
                            syncConversations()
                        }
                    }
                    is WebSocketManager.ConnectionState.Disconnected -> {
                        Log.w(TAG, "WebSocket断开: ${state.reason}")
                    }
                    is WebSocketManager.ConnectionState.MessageReceived -> {
                        Log.e(TAG, "🔥🔥🔥🔥🔥 MainViewModel收到消息通知 - messageId=${state.message.messageId}, conversationId=${state.message.conversationId}, senderId=${state.message.senderId}, receiverId=${state.message.receiverId}, content=${state.message.content.take(50)}")
                        // 处理接收到的消息（使用IO调度器确保数据库操作不阻塞）
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                Log.e(TAG, "开始处理接收到的消息 - messageId=${state.message.messageId}")
                                messageRepositoryImpl.handleReceivedMessage(state.message)
                                Log.e(TAG, "✅✅✅✅✅ 消息已保存到本地数据库 - messageId=${state.message.messageId}")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 处理接收消息失败", e)
                                e.printStackTrace()
                            }
                        }
                    }
                    is WebSocketManager.ConnectionState.MessageRecalled -> {
                        Log.d(TAG, "收到撤回消息通知 - messageId=${state.messageId}")
                        // 处理撤回消息通知
                        viewModelScope.launch {
                            try {
                                messageRepositoryImpl.handleMessageRecalled(state.messageId)
                                Log.d(TAG, "消息已撤回 - messageId=${state.messageId}")
                            } catch (e: Exception) {
                                Log.e(TAG, "处理撤回消息失败", e)
                            }
                        }
                    }
                    is WebSocketManager.ConnectionState.FriendRequestReceived -> {
                        Log.d(TAG, "收到好友请求通知 - requestId=${state.requestId}, fromUserId=${state.fromUserId}")
                        // 处理好友请求通知 - 刷新好友请求列表
                        viewModelScope.launch {
                            friendRepository.getFriendRequests()
                                .onSuccess {
                                    Log.d(TAG, "好友请求列表已更新")
                                }
                                .onFailure {
                                    Log.e(TAG, "刷新好友请求列表失败", it)
                                }
                        }
                    }
                    is WebSocketManager.ConnectionState.AccountKicked -> {
                        Log.e(TAG, "收到账号被踢通知 - reason=${state.reason}, message=${state.message}")
                        // 处理账号被踢：先发送全局事件（会立即标记为已登出并跳转），再清除数据
                        viewModelScope.launch {
                            try {
                                // 先发送全局账号被踢事件（所有Activity都会收到并立即处理）
                                // notifyAccountKicked 会将 isLoggedIn 设为 false，并触发跳转
                                com.tongxun.utils.AccountKickedManager.notifyAccountKicked(state.message)
                                Log.e(TAG, "✅ 已发送全局账号被踢事件")
                                
                                // 清除所有本地数据（在跳转的同时进行）
                                Log.e(TAG, "开始清除本地数据...")
                                authRepository.logout()
                                Log.e(TAG, "✅ 本地数据已清除")
                                
                                // 同时设置本地标志（用于MainActivity的监听，用于断开WebSocket）
                                _shouldNavigateToLogin.value = state.message
                            } catch (e: Exception) {
                                Log.e(TAG, "处理账号被踢失败", e)
                                // 即使出错也发送全局事件，确保能跳转到登录页面
                                com.tongxun.utils.AccountKickedManager.notifyAccountKicked(state.message)
                                _shouldNavigateToLogin.value = state.message
                            }
                        }
                    }
                    is WebSocketManager.ConnectionState.VoiceCallOffer -> {
                        Log.d(TAG, "收到语音通话请求 - fromUserId=${state.fromUserId}, toUserId=${state.toUserId}")
                        // 处理语音通话请求（可以显示通知或启动语音通话Activity）
                        // 这里暂时只记录日志，后续可以添加语音通话UI
                        // TODO: 实现语音通话UI和逻辑
                    }
                }
            }
            }
            
            Log.d(TAG, "✅ WebSocket监听已启动")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动WebSocket监听失败", e)
        }
        
        Log.d(TAG, "=== MainViewModel.setupWebSocketListener() 结束 ===")
    }
}

