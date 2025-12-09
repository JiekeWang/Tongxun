package com.tongxun.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.tongxun.databinding.ActivityLoginBinding
import com.tongxun.ui.main.MainActivity
import com.tongxun.utils.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化权限管理器
        permissionManager = PermissionManager(this)
        
        setupObservers()
        setupClickListeners()
        
        // 请求应用所需权限
        requestAppPermissions()
        
        // 检查自动登录（在UI初始化之后）
        checkAutoLogin()
    }
    
    /**
     * 请求应用所需的所有权限
     */
    private fun requestAppPermissions() {
        // 检查是否所有权限都已授予
        if (permissionManager.areAllPermissionsGranted()) {
            android.util.Log.d("LoginActivity", "所有权限已授予")
            return
        }
        
        // 获取未授予的权限
        val deniedPermissions = permissionManager.getDeniedPermissions()
        android.util.Log.d("LoginActivity", "需要请求的权限: ${deniedPermissions.joinToString(", ")}")
        
        // 显示权限说明对话框
        showPermissionExplanationDialog {
            // 用户点击确定后请求权限
            permissionManager.requestAllPermissions { allGranted, permissions ->
                if (allGranted) {
                    android.util.Log.d("LoginActivity", "✅ 所有权限已授予")
                    Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    android.util.Log.w("LoginActivity", "⚠️ 部分权限未授予")
                    val denied = permissions.filter { !it.value }.keys
                    android.util.Log.w("LoginActivity", "未授予的权限: ${denied.joinToString(", ")}")
                    
                    // 检查是否有权限被永久拒绝
                    val permanentlyDenied = denied.filter { permission ->
                        !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            permission
                        )
                    }
                    
                    if (permanentlyDenied.isNotEmpty()) {
                        // 有权限被永久拒绝，引导用户到设置页面
                        showPermissionSettingsDialog(permanentlyDenied)
                    } else {
                        Toast.makeText(
                            this,
                            "部分权限未授予，某些功能可能无法使用",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    
    /**
     * 显示权限说明对话框
     */
    private fun showPermissionExplanationDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("权限请求")
            .setMessage(
                "应用需要以下权限以提供完整功能：\n\n" +
                "• 通知权限：接收消息通知\n" +
                "• 存储权限：保存和选择图片、文件\n" +
                "• 相机权限：拍照和扫描二维码\n" +
                "• 录音权限：发送语音消息\n\n" +
                "请允许这些权限以确保应用正常运行。"
            )
            .setPositiveButton("确定") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("取消") { _, _ ->
                Toast.makeText(this, "部分功能可能无法使用", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示权限设置对话框（当权限被永久拒绝时）
     */
    private fun showPermissionSettingsDialog(permanentlyDenied: List<String>) {
        val permissionNames = permanentlyDenied.map { permission ->
            when (permission) {
                android.Manifest.permission.POST_NOTIFICATIONS -> "通知权限"
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限"
                android.Manifest.permission.CAMERA -> "相机权限"
                android.Manifest.permission.RECORD_AUDIO -> "录音权限"
                else -> permission
            }
        }.joinToString("、")
        
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("以下权限被拒绝：$permissionNames\n\n请在设置中手动开启这些权限。")
            .setPositiveButton("去设置") { _, _ ->
                // 打开应用设置页面
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun checkAutoLogin() {
        // 在后台协程中检查自动登录，避免阻塞UI
        lifecycleScope.launch {
            try {
                viewModel.checkAutoLogin()
                    .onSuccess {
                        // 自动登录成功，跳转到主界面
                        navigateToMain()
                    }
                    .onFailure { exception ->
                        // 自动登录失败，显示登录界面
                        // 不显示错误提示，因为这是自动检查
                        android.util.Log.d("LoginActivity", "自动登录失败: ${exception.message}")
                    }
            } catch (e: Exception) {
                // 捕获异常，避免崩溃
                android.util.Log.e("LoginActivity", "自动登录检查异常", e)
            }
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                
                binding.btnLogin.isEnabled = !state.isLoading
                
                state.error?.let { error ->
                    // 使用更长的显示时间，确保用户能看到错误信息
                    Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
                
                if (state.isSuccess) {
                    navigateToMain()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            android.util.Log.e("LoginActivity", "🔥🔥🔥 登录按钮被点击")
            
            // 防止重复点击
            if (viewModel.uiState.value.isLoading) {
                android.util.Log.w("LoginActivity", "登录正在进行中，忽略重复点击")
                return@setOnClickListener
            }
            
            val phone = binding.etPhone.text?.toString() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            
            android.util.Log.e("LoginActivity", "🔥 从输入框获取的值 - phone长度: ${phone.length}, password长度: ${password.length}")
            android.util.Log.e("LoginActivity", "🔥 phone内容: ${phone.take(3)}***")
            
            if (phone.isBlank()) {
                android.util.Log.e("LoginActivity", "❌❌❌ 手机号为空！")
                Toast.makeText(this, "请输入手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (password.isBlank()) {
                android.util.Log.e("LoginActivity", "❌❌❌ 密码为空！")
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            android.util.Log.e("LoginActivity", "✅ 调用 viewModel.login()")
            viewModel.login(phone, password)
        }
        
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        // 添加输入框焦点变化监听，实时清除错误
        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.clearError()
            }
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
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

