package com.tongxun.ui.me

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
class MeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val uploadRepository: UploadRepository
) : ViewModel() {
    
    private val TAG = "MeViewModel"
    
    private val _uiState = MutableStateFlow(MeUiState())
    val uiState: StateFlow<MeUiState> = _uiState.asStateFlow()
    
    // 防止重复操作
    private var isLoggingOut = false
    
    init {
        loadUserInfo()
    }
    
    /**
     * 加载当前用户信息
     */
    private fun loadUserInfo() {
        val currentUser = authRepository.getCurrentUser()
        _uiState.value = _uiState.value.copy(
            currentUser = currentUser
        )
    }
    
    /**
     * 退出登录
     */
    fun logout() {
        // 防止重复操作
        if (isLoggingOut || _uiState.value.isLoggingOut) {
            return
        }
        
        isLoggingOut = true
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoggingOut = true,
                    error = null
                )
                
                // 调用登出API并清理本地数据
                authRepository.logout()
                
                _uiState.value = _uiState.value.copy(
                    isLoggingOut = false,
                    isLoggedOut = true
                )
            } catch (e: Exception) {
                // 即使出错也标记为已登出，确保用户能退出
                _uiState.value = _uiState.value.copy(
                    isLoggingOut = false,
                    isLoggedOut = true,
                    error = "退出登录时发生错误，但已清理本地数据"
                )
                Log.e(TAG, "退出登录异常", e)
            } finally {
                isLoggingOut = false
            }
        }
    }
    
    /**
     * 上传头像
     */
    fun uploadAvatar(uri: Uri) {
        // 验证 URI 有效性
        if (uri == Uri.EMPTY) {
            _uiState.value = _uiState.value.copy(error = "图片 URI 无效")
            Log.w(TAG, "上传头像失败：URI 为空")
            return
        }
        
        val currentUser = _uiState.value.currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(error = "用户未登录")
            Log.w(TAG, "上传头像失败：用户未登录")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
                   try {
                       Log.d(TAG, "开始上传头像 - URI: $uri, userId: ${currentUser.userId}")
                       uploadRepository.uploadImage(uri)
                           .onSuccess { uploadResponse ->
                               // 详细记录响应数据
                               Log.d(TAG, "✅ 上传API调用成功")
                               Log.d(TAG, "响应数据 - fileId: ${uploadResponse.fileId}")
                               Log.d(TAG, "响应数据 - fileUrl: '${uploadResponse.fileUrl}' (是否为空: ${uploadResponse.fileUrl.isBlank()})")
                               Log.d(TAG, "响应数据 - fileName: ${uploadResponse.fileName}, fileSize: ${uploadResponse.fileSize}")
                               
                               val newAvatarUrl = uploadResponse.fileUrl
                               Log.d(TAG, "提取的URL: '$newAvatarUrl'")
                               Log.d(TAG, "当前用户信息 - userId: ${currentUser.userId}, nickname: ${currentUser.nickname}, avatar: ${currentUser.avatar}")
                               
                               // 验证头像URL
                               if (newAvatarUrl.isNullOrBlank()) {
                                   Log.e(TAG, "❌ 头像URL为空，无法更新")
                                   Log.e(TAG, "完整响应对象: ${uploadResponse}")
                                   _uiState.value = _uiState.value.copy(
                                       isLoading = false,
                                       error = "头像上传成功，但URL为空。fileId: ${uploadResponse.fileId}"
                                   )
                                   return@launch
                               }
                        
                        // 更新用户头像到服务器和本地
                        val updatedUser = currentUser.copy(avatar = newAvatarUrl)
                        Log.d(TAG, "准备更新的用户信息 - userId: ${updatedUser.userId}, nickname: ${updatedUser.nickname}, avatar: ${updatedUser.avatar}")
                        try {
                            userRepository.updateUser(updatedUser)
                            
                            // 刷新用户信息，确保获取服务器返回的最新数据
                            val refreshedUser = userRepository.getUserById(currentUser.userId)
                            
                            // 更新UI状态，使用服务器返回的最新数据
                            _uiState.value = _uiState.value.copy(
                                currentUser = refreshedUser ?: updatedUser, // 如果刷新失败，使用更新后的用户数据
                                isLoading = false,
                                successMessage = "头像更新成功"
                            )
                            Log.d(TAG, "用户头像已更新 - avatar: ${refreshedUser?.avatar ?: updatedUser.avatar}")
                        } catch (e: Exception) {
                            Log.e(TAG, "更新用户信息失败", e)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "头像上传成功，但更新用户信息失败: ${e.message}"
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "上传头像失败: ${error.message ?: "未知错误"}"
                        )
                        Log.e(TAG, "头像上传失败", error)
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "上传头像异常: ${e.message ?: "未知错误"}"
                )
                Log.e(TAG, "头像上传异常", e)
            }
        }
    }
    
    /**
     * 刷新用户信息
     */
    fun refreshUserInfo() {
        loadUserInfo()
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
    
    data class MeUiState(
        val currentUser: UserEntity? = null,
        val isLoggingOut: Boolean = false,
        val isLoggedOut: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null
    )
}

