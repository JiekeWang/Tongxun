package com.tongxun.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tongxun.databinding.ActivityRegisterBinding
import com.tongxun.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                
                binding.btnRegister.isEnabled = !state.isLoading
                
                state.error?.let { error ->
                    // 使用更长的显示时间，确保用户能看到错误信息
                    Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
                
                if (state.isSuccess) {
                    navigateToMain()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            // 防止重复点击
            if (viewModel.uiState.value.isLoading) {
                return@setOnClickListener
            }
            
            val phone = binding.etPhone.text?.toString() ?: ""
            val nickname = binding.etNickname.text?.toString() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            val confirmPassword = binding.etConfirmPassword.text?.toString() ?: ""
            
            viewModel.register(phone, nickname, password, confirmPassword)
        }
        
        // 添加输入框焦点变化监听，实时清除错误
        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.clearError()
            }
        }
        binding.etNickname.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.clearError()
            }
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.clearError()
            }
        }
        binding.etConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.clearError()
            }
        }
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

