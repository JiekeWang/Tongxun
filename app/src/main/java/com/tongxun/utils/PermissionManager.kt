package com.tongxun.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 权限管理工具类
 * 用于统一管理应用的运行时权限请求
 */
class PermissionManager(private val activity: AppCompatActivity) {
    
    // 权限请求启动器
    private val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        onPermissionResult?.invoke(allGranted, permissions)
    }
    
    // 权限请求结果回调
    private var onPermissionResult: ((Boolean, Map<String, Boolean>) -> Unit)? = null
    
    /**
     * 获取应用所需的所有权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        // 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // 存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的媒体权限
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            // Android 12 及以下使用旧权限
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        // 相机权限
        permissions.add(Manifest.permission.CAMERA)
        
        // 录音权限
        permissions.add(Manifest.permission.RECORD_AUDIO)
        
        return permissions.toTypedArray()
    }
    
    /**
     * 检查所有权限是否已授予
     */
    fun areAllPermissionsGranted(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查特定权限是否已授予
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取未授予的权限列表
     */
    fun getDeniedPermissions(): List<String> {
        val permissions = getRequiredPermissions()
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 请求所有权限
     * @param onResult 权限请求结果回调，参数1：是否全部授予，参数2：每个权限的授予状态
     */
    fun requestAllPermissions(onResult: (Boolean, Map<String, Boolean>) -> Unit) {
        val permissions = getRequiredPermissions()
        val deniedPermissions = getDeniedPermissions()
        
        if (deniedPermissions.isEmpty()) {
            // 所有权限都已授予
            val allGranted = permissions.associateWith { true }
            onResult(true, allGranted)
            return
        }
        
        // 保存回调
        onPermissionResult = onResult
        
        // 请求未授予的权限
        requestPermissionLauncher.launch(deniedPermissions.toTypedArray())
    }
    
    /**
     * 请求特定权限
     * @param permissions 要请求的权限数组
     * @param onResult 权限请求结果回调
     */
    fun requestPermissions(
        permissions: Array<String>,
        onResult: (Boolean, Map<String, Boolean>) -> Unit
    ) {
        val deniedPermissions = permissions.filter { !isPermissionGranted(it) }
        
        if (deniedPermissions.isEmpty()) {
            // 所有权限都已授予
            val allGranted = permissions.associateWith { true }
            onResult(true, allGranted)
            return
        }
        
        // 保存回调
        onPermissionResult = onResult
        
        // 请求未授予的权限
        requestPermissionLauncher.launch(deniedPermissions.toTypedArray())
    }
    
    /**
     * 检查并请求通知权限（Android 13+）
     */
    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                onResult(true)
            } else {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { allGranted, _ ->
                    onResult(allGranted)
                }
            }
        } else {
            // Android 13以下不需要请求通知权限
            onResult(true)
        }
    }
    
    /**
     * 检查并请求存储权限
     */
    fun requestStoragePermission(onResult: (Boolean) -> Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            val perms = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            perms.toTypedArray()
        }
        
        requestPermissions(permissions) { allGranted, _ ->
            onResult(allGranted)
        }
    }
    
    /**
     * 检查并请求相机权限
     */
    fun requestCameraPermission(onResult: (Boolean) -> Unit) {
        if (isPermissionGranted(Manifest.permission.CAMERA)) {
            onResult(true)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA)) { allGranted, _ ->
                onResult(allGranted)
            }
        }
    }
    
    /**
     * 检查并请求录音权限
     */
    fun requestAudioPermission(onResult: (Boolean) -> Unit) {
        if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            onResult(true)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) { allGranted, _ ->
                onResult(allGranted)
            }
        }
    }
}

