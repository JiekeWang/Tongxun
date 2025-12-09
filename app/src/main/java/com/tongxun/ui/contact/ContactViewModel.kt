package com.tongxun.ui.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.local.entity.FriendEntity
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val authRepository: AuthRepository,
    private val webSocketManager: com.tongxun.data.remote.WebSocketManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "ContactViewModel"
        private const val SYNC_THROTTLE_MS = 5_000L // 5秒内只同步一次
    }
    
    private val _friendRequestCount = MutableStateFlow(0)
    val friendRequestCount: StateFlow<Int> = _friendRequestCount.asStateFlow()
    
    private var lastSyncTime = 0L
    private var syncJob: Job? = null
    
    init {
        // 监听WebSocket好友请求通知
        setupWebSocketListener()
    }
    
    private fun setupWebSocketListener() {
        viewModelScope.launch {
            webSocketManager.connect()
                .collect { state ->
                    when (state) {
                        is com.tongxun.data.remote.WebSocketManager.ConnectionState.FriendRequestReceived -> {
                            // 收到好友请求通知，自动刷新数量
                            Log.d(TAG, "收到好友请求通知")
                            loadFriendRequestCount()
                        }
                        is com.tongxun.data.remote.WebSocketManager.ConnectionState.MessageReceived -> {
                            // 忽略消息，由MainViewModel处理
                            Log.d(TAG, "ContactViewModel收到消息，但忽略（由MainViewModel处理）")
                        }
                        else -> {
                            // 其他状态忽略
                        }
                    }
                }
        }
    }
    
    val friends: StateFlow<List<FriendEntity>> = run {
        val currentUser = authRepository.getCurrentUser()
        Log.d(TAG, "ContactViewModel.init - 初始化friends Flow, currentUser: ${currentUser?.userId?.take(8)}...")
        val friendsFlow = if (currentUser != null) {
            friendRepository.getFriends(currentUser.userId)
        } else {
            Log.w(TAG, "⚠️ 用户未登录，friends Flow 返回空列表")
            kotlinx.coroutines.flow.flowOf(emptyList<FriendEntity>())
        }
        
        friendsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
            .also { stateFlow ->
                // 添加日志监听（不影响原始Flow）
                viewModelScope.launch {
                    stateFlow.collect { friends ->
                        Log.d(TAG, "✅ friends Flow 更新 - 共 ${friends.size} 个好友")
                        if (friends.isNotEmpty()) {
                            friends.take(3).forEach { friend ->
                                Log.d(TAG, "  好友: ${friend.friendId.take(8)}..., 昵称: ${friend.nickname ?: friend.remark ?: "无"}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ friends 列表为空")
                        }
                    }
                }
            }
    }
    
    fun loadFriendRequestCount() {
        viewModelScope.launch {
            friendRepository.getFriendRequests()
                .onSuccess { response ->
                    _friendRequestCount.value = response.received.size
                    Log.d(TAG, "好友请求数量已更新: ${response.received.size}")
                }
                .onFailure { e ->
                    Log.w(TAG, "加载好友请求数量失败", e)
                    // 保持当前值，不更新
                }
        }
    }
    
    /**
     * 同步好友列表（带防抖机制）
     * 避免频繁调用导致性能问题
     */
    fun syncFriends() {
        val currentTime = System.currentTimeMillis()
        
        // 检查是否需要节流
        if (currentTime - lastSyncTime < SYNC_THROTTLE_MS) {
            Log.d(TAG, "同步请求被节流，距离上次同步仅 ${currentTime - lastSyncTime}ms")
            return
        }
        
        // 取消之前的同步任务（如果还在运行）
        syncJob?.cancel()
        
        val currentUser = authRepository.getCurrentUser()
        if (currentUser == null) {
            Log.w(TAG, "同步好友列表失败：用户未登录")
            return
        }
        
        lastSyncTime = currentTime
        syncJob = viewModelScope.launch {
            if (!isActive) return@launch
            
            friendRepository.syncFriendsFromServer(currentUser.userId)
                .onSuccess {
                    Log.d(TAG, "好友列表同步成功")
                }
                .onFailure { e ->
                    Log.e(TAG, "好友列表同步失败", e)
                    // 不向UI暴露错误，因为用户可能离线
                }
        }
    }
    
    /**
     * 强制同步好友列表（不受节流限制）
     * 用于接受好友请求后立即同步或从其他页面返回时刷新
     */
    fun forceSyncFriends() {
        syncJob?.cancel()
        
        val currentUser = authRepository.getCurrentUser()
        if (currentUser == null) {
            Log.w(TAG, "强制同步好友列表失败：用户未登录")
            return
        }
        
        Log.d(TAG, "开始强制同步好友列表 - userId: ${currentUser.userId.take(8)}...")
        lastSyncTime = System.currentTimeMillis()
        syncJob = viewModelScope.launch {
            if (!isActive) return@launch
            
            friendRepository.syncFriendsFromServer(currentUser.userId)
                .onSuccess {
                    Log.d(TAG, "✅ 好友列表强制同步成功")
                    // 同步成功后，friends Flow 会自动更新（因为它监听数据库变化）
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ 好友列表强制同步失败: ${e.message}", e)
                    // 即使同步失败，也尝试从本地数据库加载已存在的好友
                    Log.d(TAG, "同步失败，但本地数据库中的好友列表仍会显示")
                }
        }
    }
}

