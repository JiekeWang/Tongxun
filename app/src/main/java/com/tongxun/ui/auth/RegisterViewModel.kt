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
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    
    // 防止重复提交
    private var isRegistering = false
    
    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
        private const val MAX_PASSWORD_LENGTH = 50
        private const val MIN_NICKNAME_LENGTH = 1
        private const val MAX_NICKNAME_LENGTH = 50
        private const val PHONE_NUMBER_LENGTH = 11
    }
    
    fun register(phoneNumber: String, nickname: String, password: String, confirmPassword: String) {
        // 防止重复提交
        if (isRegistering || _uiState.value.isLoading) {
            return
        }
        
        // 清理输入：去除首尾空格
        val trimmedPhone = phoneNumber.trim()
        val trimmedNickname = nickname.trim()
        val trimmedPassword = password.trim()
        val trimmedConfirmPassword = confirmPassword.trim()
        
        // 验证字段非空
        val validationError = validateInputs(trimmedPhone, trimmedNickname, trimmedPassword, trimmedConfirmPassword)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(error = validationError)
            return
        }
        
        // 开始注册
        isRegistering = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            
            authRepository.register(trimmedPhone, trimmedPassword, trimmedNickname)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                    isRegistering = false
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "注册失败，请稍后重试"
                    )
                    isRegistering = false
                }
        }
    }
    
    /**
     * 验证输入字段
     * @return 错误信息，如果验证通过返回 null
     */
    private fun validateInputs(
        phoneNumber: String,
        nickname: String,
        password: String,
        confirmPassword: String
    ): String? {
        // 验证手机号
        when {
            phoneNumber.isBlank() -> return "手机号不能为空"
            phoneNumber.length != PHONE_NUMBER_LENGTH -> return "手机号必须是11位数字"
            !isValidPhone(phoneNumber) -> return "手机号格式不正确，请输入有效的中国手机号"
        }
        
        // 验证昵称
        when {
            nickname.isBlank() -> return "昵称不能为空"
            nickname.length < MIN_NICKNAME_LENGTH -> return "昵称至少需要${MIN_NICKNAME_LENGTH}个字符"
            nickname.length > MAX_NICKNAME_LENGTH -> return "昵称不能超过${MAX_NICKNAME_LENGTH}个字符"
            !isValidNickname(nickname) -> return "昵称包含非法字符，只能包含中文、英文、数字和下划线"
        }
        
        // 验证密码
        when {
            password.isBlank() -> return "密码不能为空"
            password.length < MIN_PASSWORD_LENGTH -> return "密码长度至少${MIN_PASSWORD_LENGTH}位"
            password.length > MAX_PASSWORD_LENGTH -> return "密码长度不能超过${MAX_PASSWORD_LENGTH}位"
            !isValidPassword(password) -> return "密码只能包含字母、数字和常用符号"
        }
        
        // 验证确认密码
        when {
            confirmPassword.isBlank() -> return "确认密码不能为空"
            password != confirmPassword -> return "两次输入的密码不一致"
        }
        
        return null
    }
    
    /**
     * 验证手机号格式
     * 中国手机号：1开头，第二位是3-9，共11位数字
     */
    private fun isValidPhone(phone: String): Boolean {
        return phone.matches(Regex("^1[3-9]\\d{9}$"))
    }
    
    /**
     * 验证昵称格式
     * 允许：中文、英文、数字、下划线、连字符
     */
    private fun isValidNickname(nickname: String): Boolean {
        // 允许中文、英文、数字、下划线、连字符、空格（但首尾不能是空格，已在trim后验证）
        return nickname.matches(Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\s]+$"))
    }
    
    /**
     * 验证密码格式
     * 允许：字母、数字、常用符号（!@#$%^&*()_+-=[]{}|;:,.<>?）
     */
    private fun isValidPassword(password: String): Boolean {
        // 允许字母、数字、常用符号
        return password.matches(Regex("^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{}|;:,.<>?]+$"))
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    data class RegisterUiState(
        val isLoading: Boolean = false,
        val isSuccess: Boolean = false,
        val error: String? = null
    )
}

