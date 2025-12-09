package com.tongxun.ui.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.local.entity.UserEntity
import com.tongxun.data.repository.UploadRepository
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val uploadRepository: UploadRepository
) : ViewModel() {
    
    private val TAG = "UserProfileViewModel"
    
    private val _userInfo = MutableStateFlow<UserEntity?>(null)
    val userInfo: StateFlow<UserEntity?> = _userInfo.asStateFlow()
    
    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()
    
    fun loadUserInfo(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            userRepository.getUserById(userId)
                ?.let {
                    _userInfo.value = it
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "用户不存在"
                    )
                }
        }
    }
    
    fun getCurrentUserId(): String {
        return authRepository.getCurrentUser()?.userId ?: ""
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 上传头像
     */
    fun uploadAvatar(uri: Uri) {
        val currentUser = _userInfo.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            uploadRepository.uploadImage(uri)
                .onSuccess { uploadResponse ->
                    // 更新用户头像
                    val updatedUser = currentUser.copy(avatar = uploadResponse.fileUrl)
                    try {
                        userRepository.updateUser(updatedUser)
                        
                        // 刷新用户信息，确保获取服务器返回的最新数据
                        val refreshedUser = userRepository.getUserById(currentUser.userId)
                        
                        // 更新本地StateFlow，使用服务器返回的最新数据
                        _userInfo.value = refreshedUser ?: updatedUser
                        _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "头像更新成功")
                        Log.d(TAG, "用户头像已更新 - avatar: ${refreshedUser?.avatar ?: updatedUser.avatar}")
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "更新头像失败: ${e.message}"
                        )
                        Log.e(TAG, "更新头像失败", e)
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "上传头像失败: ${error.message}"
                    )
                }
        }
    }
    
    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
    
    data class UserProfileUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null
    )
}

