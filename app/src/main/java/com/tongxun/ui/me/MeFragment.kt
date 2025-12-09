package com.tongxun.ui.me

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.tongxun.databinding.FragmentMeBinding
import com.tongxun.ui.auth.LoginActivity
import com.tongxun.utils.ImageUrlUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MeFragment : Fragment() {
    
    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MeViewModel by viewModels()
    
    private var cameraImageUri: Uri? = null
    
    // 图片选择器（相册）
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAvatar(it)
        }
    }
    
    // 拍照
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let {
                viewModel.uploadAvatar(it)
            }
        } else {
            Toast.makeText(requireContext(), "拍照取消或失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val readMediaImagesGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        val readExternalStorageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        
        if (cameraGranted && (readMediaImagesGranted || readExternalStorageGranted)) {
            showImageSourceDialog()
        } else {
            Toast.makeText(requireContext(), "需要相机和存储权限才能上传头像", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // 更新用户信息显示
                state.currentUser?.let { user ->
                    binding.tvUserName.text = user.nickname
                    binding.tvPhoneNumber.text = user.phoneNumber
                    binding.tvSignature.text = user.signature ?: "暂无个性签名"
                    
                    // 加载头像
                    loadAvatar(user.avatar)
                } ?: run {
                    // 用户信息为空
                    binding.tvUserName.text = "未登录"
                    binding.tvPhoneNumber.text = ""
                    binding.tvSignature.text = ""
                    binding.ivAvatar.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
                }
                
                // 退出登录状态
                if (state.isLoggedOut) {
                    navigateToLogin()
                }
                
                // 错误提示
                state.error?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
                
                // 成功提示
                state.successMessage?.let { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        // 头像点击上传
        binding.ivAvatar.setOnClickListener {
            showImageSourceDialog()
        }
        
        // 退出登录
        binding.btnLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定") { _, _ ->
                    viewModel.logout()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun loadAvatar(avatarUrl: String?) {
        val fullUrl = ImageUrlUtils.getFullImageUrl(avatarUrl)
        
        // 如果 URL 为空，显示默认头像
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
    
    private fun showImageSourceDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("选择图片来源")
            .setItems(arrayOf("拍照", "从相册选择")) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndTakePicture()
                    1 -> checkStoragePermissionAndPickImage()
                }
            }
            .show()
    }
    
    private fun checkCameraPermissionAndTakePicture() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            takePicture()
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            } else {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
            requestPermissionLauncher.launch(permissions)
        }
    }
    
    private fun checkStoragePermissionAndPickImage() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (permissions.all {
                ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            pickImage()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }
    
    private fun takePicture() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: Exception) {
            Toast.makeText(requireContext(), "创建图片文件失败", Toast.LENGTH_SHORT).show()
            null
        }
        photoFile?.also {
            cameraImageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                it
            )
            cameraLauncher.launch(cameraImageUri)
        }
    }
    
    private fun pickImage() {
        imagePickerLauncher.launch("image/*")
    }
    
    private fun createImageFile(): File {
        val timeStamp: String =
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())
        val storageDir: File? = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
    
    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        activity?.finish()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

