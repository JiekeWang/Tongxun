package com.tongxun.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.tongxun.databinding.ActivityUserProfileBinding
import com.tongxun.domain.repository.UserRepository
import com.tongxun.ui.chat.ChatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserProfileActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityUserProfileBinding
    private val viewModel: UserProfileViewModel by viewModels()
    
    private var userId: String? = null
    private var isCurrentUser: Boolean = false
    
    // 图片选择器（相册）
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAvatar(it)
        }
    }
    
    // 拍照
    private var cameraImageUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let {
                viewModel.uploadAvatar(it)
            }
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImagePickerDialog()
        } else {
            Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePicture()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userId = intent.getStringExtra("user_id")
        val currentUserId = viewModel.getCurrentUserId()
        isCurrentUser = userId == currentUserId
        
        setupToolbar()
        setupObservers()
        setupClickListeners()
        
        userId?.let {
            viewModel.loadUserInfo(it)
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.userInfo.collect { user ->
                user?.let {
                    binding.apply {
                        tvUserName.text = it.nickname
                        tvUserId.text = "ID: ${it.userId}"
                        tvPhoneNumber.text = it.phoneNumber
                        tvSignature.text = it.signature ?: "这个人很懒，什么都没有留下"
                        
                        // 加载头像
                        loadAvatar(it.avatar)
                        
                        // 如果是当前用户，隐藏发送消息按钮，头像可点击上传
                        btnSendMessage.visibility = if (isCurrentUser) {
                            android.view.View.GONE
                        } else {
                            android.view.View.VISIBLE
                        }
                        
                        // 当前用户的头像可点击上传
                        ivAvatar.isClickable = isCurrentUser
                        ivAvatar.setOnClickListener {
                            if (isCurrentUser && checkPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                                showImagePickerDialog()
                            } else if (isCurrentUser) {
                                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                            }
                        }
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                state.error?.let { error ->
                    android.widget.Toast.makeText(this@UserProfileActivity, error, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
                state.successMessage?.let { message ->
                    android.widget.Toast.makeText(this@UserProfileActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                    // 头像更新成功后，重新加载用户信息以刷新UI
                    userId?.let { viewModel.loadUserInfo(it) }
                }
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (isCurrentUser) {
            menuInflater.inflate(com.tongxun.R.menu.user_profile_menu, menu)
        }
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            com.tongxun.R.id.menu_edit_profile -> {
                // TODO: 打开编辑资料页面
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSendMessage.setOnClickListener {
            userId?.let { targetId ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("target_id", targetId)
                    putExtra("target_name", viewModel.userInfo.value?.nickname ?: "")
                }
                startActivity(intent)
            }
        }
    }
    
    private fun loadAvatar(avatarUrl: String?) {
        val fullUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(avatarUrl)
        if (fullUrl == null) {
            binding.ivAvatar.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
            return
        }
        
        // 添加时间戳参数来避免缓存问题，强制刷新
        // 使用头像URL的最后修改时间或当前时间作为缓存key的一部分
        val urlWithTimestamp = if (fullUrl.contains("?")) {
            "$fullUrl&t=${System.currentTimeMillis()}"
        } else {
            "$fullUrl?t=${System.currentTimeMillis()}"
        }
        
        Glide.with(this)
            .load(urlWithTimestamp)
            .skipMemoryCache(false) // 允许内存缓存以提高性能
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // 缓存所有版本的图片
            .placeholder(com.tongxun.R.drawable.ic_launcher_round)
            .error(com.tongxun.R.drawable.ic_launcher_round)
            .centerCrop()
            .into(binding.ivAvatar)
    }
    
    private fun showImagePickerDialog() {
        android.app.AlertDialog.Builder(this)
            .setItems(arrayOf("拍照", "从相册选择")) { _, which ->
                when (which) {
                    0 -> {
                        if (checkPermission(Manifest.permission.CAMERA)) {
                            takePicture()
                        } else {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> {
                        imagePickerLauncher.launch("image/*")
                    }
                }
            }
            .show()
    }
    
    private fun takePicture() {
        try {
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val imageFile = java.io.File.createTempFile(imageFileName, ".jpg", storageDir)
            
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile
            )
            
            cameraLauncher.launch(cameraImageUri)
        } catch (e: Exception) {
            Toast.makeText(this, "无法创建图片文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
