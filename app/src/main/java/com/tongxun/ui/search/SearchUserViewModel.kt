package com.tongxun.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.local.entity.UserEntity
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.domain.repository.FriendRepository
import com.tongxun.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchUserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val friendRepository: FriendRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUserUiState())
    val uiState: StateFlow<SearchUserUiState> = _uiState.asStateFlow()
    
    fun updateSearchText(text: String) {
        _uiState.value = _uiState.value.copy(
            searchText = text,
            searchResult = null
        )
    }
    
    fun searchUser(searchText: String) {
        val trimmedText = searchText.trim()
        
        if (trimmedText.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "搜索内容不能为空"
            )
            return
        }
        
        // 防止重复搜索
        if (_uiState.value.isLoading) {
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    searchResult = null
                )
                
                val user = when {
                    trimmedText.matches(Regex("^1[3-9]\\d{9}$")) -> {
                        // 手机号搜索
                        android.util.Log.d("SearchUserViewModel", "按手机号搜索: $trimmedText")
                        userRepository.searchUser(phone = trimmedText)
                    }
                    trimmedText.length >= 1 -> {
                        // 尝试作为用户ID搜索（支持UUID或普通ID）
                        android.util.Log.d("SearchUserViewModel", "按用户ID搜索: $trimmedText")
                        userRepository.searchUser(userId = trimmedText)
                    }
                    else -> {
                        android.util.Log.w("SearchUserViewModel", "搜索文本格式不正确: $trimmedText")
                        null
                    }
                }
                
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        searchResult = user,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未找到该用户，请检查手机号或用户ID是否正确"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "搜索失败：${e.message ?: "网络错误"}"
                )
                android.util.Log.e("SearchUserViewModel", "搜索用户异常", e)
            }
        }
    }
    
    fun sendFriendRequest(friendId: String, message: String?) {
        val currentUser = authRepository.getCurrentUser() ?: run {
            _uiState.value = _uiState.value.copy(error = "未登录")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            friendRepository.addFriend(currentUser.userId, friendId, message)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        friendRequestSent = true
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "发送好友请求失败"
                    )
                }
        }
    }
    
    @Suppress("UNUSED_PARAMETER")
    fun checkIfFriend(friendId: String, callback: (Boolean) -> Unit) {
        authRepository.getCurrentUser() ?: run {
            callback(false)
            return
        }
        
        viewModelScope.launch {
            // 简化处理：通过FriendRepository检查
            // 实际应该查询本地数据库或API
            // TODO: 使用 friendId 参数查询本地数据库或API
            callback(false)
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearFriendRequestSent() {
        _uiState.value = _uiState.value.copy(friendRequestSent = false)
    }
    
    data class SearchUserUiState(
        val isLoading: Boolean = false,
        val searchText: String = "",
        val searchResult: UserEntity? = null,
        val error: String? = null,
        val friendRequestSent: Boolean = false
    )
}

