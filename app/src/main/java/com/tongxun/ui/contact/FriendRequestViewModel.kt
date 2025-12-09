package com.tongxun.ui.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.remote.WebSocketManager
import com.tongxun.data.remote.dto.FriendRequestDto
import com.tongxun.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendRequestViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val webSocketManager: WebSocketManager,
    private val authRepository: com.tongxun.domain.repository.AuthRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "FriendRequestViewModel"
    }
    
    private val _friendRequests = MutableStateFlow<List<FriendRequestDto>>(emptyList())
    val friendRequests: StateFlow<List<FriendRequestDto>> = _friendRequests.asStateFlow()
    
    private val _uiState = MutableStateFlow(FriendRequestUiState())
    val uiState: StateFlow<FriendRequestUiState> = _uiState.asStateFlow()
    
    init {
        // 监听WebSocket好友请求通知
        setupWebSocketListener()
    }
    
    private fun setupWebSocketListener() {
        viewModelScope.launch {
            webSocketManager.connect()
                .collect { state ->
                    when (state) {
                        is WebSocketManager.ConnectionState.FriendRequestReceived -> {
                            // 收到好友请求通知，自动刷新列表
                            loadFriendRequests()
                        }
                        is WebSocketManager.ConnectionState.MessageReceived -> {
                            // 忽略消息，由MainViewModel处理
                            Log.d(TAG, "FriendRequestViewModel收到消息，但忽略（由MainViewModel处理）")
                        }
                        else -> {
                            // 其他状态忽略
                        }
                    }
                }
        }
    }
    
    fun loadFriendRequests() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            friendRepository.getFriendRequests()
                .onSuccess { response ->
                    _friendRequests.value = response.received
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    Log.d(TAG, "好友请求列表加载成功 - 共 ${response.received.size} 个")
                }
                .onFailure { exception ->
                    Log.e(TAG, "加载好友请求列表失败", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "加载好友请求失败"
                    )
                }
        }
    }
    
    fun acceptRequest(requestId: String) {
        // 输入验证
        if (requestId.isBlank()) {
            Log.w(TAG, "接受好友请求失败：请求ID为空")
            _uiState.value = _uiState.value.copy(error = "请求ID不能为空")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            friendRepository.acceptFriendRequest(requestId)
                .onSuccess {
                    Log.d(TAG, "✅ 接受好友请求成功 - requestId: ${requestId.take(8)}...")
                    
                    // 接受好友请求成功后，立即同步好友列表
                    val currentUser = authRepository.getCurrentUser()
                    if (currentUser != null) {
                        Log.d(TAG, "开始同步好友列表 - userId: ${currentUser.userId.take(8)}...")
                        friendRepository.syncFriendsFromServer(currentUser.userId)
                            .onSuccess {
                                Log.d(TAG, "✅ 好友列表同步成功，好友列表已更新")
                            }
                            .onFailure { e ->
                                Log.w(TAG, "⚠️ 好友列表同步失败（不影响接受操作）: ${e.message}", e)
                                // 不更新UI错误状态，因为接受操作已成功
                                // 用户返回联系人页面时会再次尝试同步
                            }
                    } else {
                        Log.w(TAG, "⚠️ 无法同步好友列表：当前用户为null")
                    }
                    
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    loadFriendRequests() // 重新加载请求列表
                }
                .onFailure { exception ->
                    Log.e(TAG, "接受好友请求失败 - requestId: ${requestId.take(8)}...", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "接受请求失败"
                    )
                }
        }
    }
    
    fun rejectRequest(requestId: String) {
        // 输入验证
        if (requestId.isBlank()) {
            Log.w(TAG, "拒绝好友请求失败：请求ID为空")
            _uiState.value = _uiState.value.copy(error = "请求ID不能为空")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            friendRepository.rejectFriendRequest(requestId)
                .onSuccess {
                    Log.d(TAG, "拒绝好友请求成功 - requestId: ${requestId.take(8)}...")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    loadFriendRequests() // 重新加载列表
                }
                .onFailure { exception ->
                    Log.e(TAG, "拒绝好友请求失败 - requestId: ${requestId.take(8)}...", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "拒绝请求失败"
                    )
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    data class FriendRequestUiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )
}

