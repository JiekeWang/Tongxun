package com.tongxun.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    // 防止重复提交
    private var isLoggingIn = false
    
    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
        private const val PHONE_NUMBER_LENGTH = 11
    }
    
    fun login(phoneNumber: String, password: String) {
        android.util.Log.e("LoginViewModel", "🔥🔥🔥 === login() 被调用 ===")
        android.util.Log.e("LoginViewModel", "🔥 输入参数 - phoneNumber长度: ${phoneNumber.length}, password长度: ${password.length}")
        android.util.Log.e("LoginViewModel", "🔥 phoneNumber内容: ${phoneNumber.take(3)}***")
        
        // 防止重复提交
        if (isLoggingIn || _uiState.value.isLoading) {
            android.util.Log.w("LoginViewModel", "登录正在进行中，忽略重复请求")
            return
        }
        
        // 清理输入：去除首尾空格
        val trimmedPhone = phoneNumber.trim()
        val trimmedPassword = password.trim()
        android.util.Log.e("LoginViewModel", "🔥 清理后 - phoneNumber长度: ${trimmedPhone.length}, password长度: ${trimmedPassword.length}")
        
        if (trimmedPhone.isBlank()) {
            android.util.Log.e("LoginViewModel", "❌❌❌ 清理后的手机号为空！")
            _uiState.value = _uiState.value.copy(error = "手机号不能为空")
            return
        }
        
        if (trimmedPassword.isBlank()) {
            android.util.Log.e("LoginViewModel", "❌❌❌ 清理后的密码为空！")
            _uiState.value = _uiState.value.copy(error = "密码不能为空")
            return
        }
        
        // 验证输入
        val validationError = validateInputs(trimmedPhone, trimmedPassword)
        if (validationError != null) {
            android.util.Log.e("LoginViewModel", "❌ 输入验证失败: $validationError")
            _uiState.value = _uiState.value.copy(error = validationError)
            return
        }
        
        android.util.Log.e("LoginViewModel", "✅ 输入验证通过，开始登录")
        
        // 开始登录
        isLoggingIn = true
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null
                )
                
                authRepository.login(trimmedPhone, trimmedPassword)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isSuccess = true
                        )
                        isLoggingIn = false
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "登录失败，请稍后重试"
                        )
                        isLoggingIn = false
                    }
            } catch (e: Exception) {
                // 捕获未预期的异常，避免崩溃
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "登录过程中发生错误，请稍后重试"
                )
                isLoggingIn = false
                android.util.Log.e("LoginViewModel", "登录异常", e)
            }
        }
    }
    
    /**
     * 验证输入字段
     * @return 错误信息，如果验证通过返回 null
     */
    private fun validateInputs(phoneNumber: String, password: String): String? {
        // 验证手机号
        when {
            phoneNumber.isBlank() -> return "手机号不能为空"
            phoneNumber.length != PHONE_NUMBER_LENGTH -> return "手机号必须是11位数字"
            !isValidPhone(phoneNumber) -> return "手机号格式不正确，请输入有效的中国手机号"
        }
        
        // 验证密码
        when {
            password.isBlank() -> return "密码不能为空"
            password.length < MIN_PASSWORD_LENGTH -> return "密码长度至少${MIN_PASSWORD_LENGTH}位"
        }
        
        return null
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    suspend fun checkAutoLogin(): Result<com.tongxun.data.local.entity.UserEntity> {
        return authRepository.checkAutoLogin()
    }
    
    private fun isValidPhone(phone: String): Boolean {
        return phone.matches(Regex("^1[3-9]\\d{9}$"))
    }
    
    data class LoginUiState(
        val isLoading: Boolean = false,
        val isSuccess: Boolean = false,
        val error: String? = null
    )
}

