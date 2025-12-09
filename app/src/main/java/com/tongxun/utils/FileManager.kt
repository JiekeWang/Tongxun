package com.tongxun.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.provider.OpenableColumns

object FileManager {
    
    private const val APP_DIR = "Tongxun"
    private const val IMAGES_DIR = "images"
    private const val FILES_DIR = "files"
    private const val CACHE_DIR = "cache"
    
    /**
     * 获取应用文件目录
     */
    fun getAppDir(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
        return if (externalDir != null) {
            File(externalDir, APP_DIR)
        } else {
            File(context.filesDir, APP_DIR)
        }.apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 获取图片目录
     */
    fun getImagesDir(context: Context): File {
        return File(getAppDir(context), IMAGES_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 获取文件目录
     */
    fun getFilesDir(context: Context): File {
        return File(getAppDir(context), FILES_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 获取缓存目录
     */
    fun getCacheDir(context: Context): File {
        return File(getAppDir(context), CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 保存文件到本地
     */
    suspend fun saveFile(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        isImage: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = if (isImage) getImagesDir(context) else getFilesDir(context)
            val file = File(dir, fileName)
            
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从Uri保存文件
     */
    suspend fun saveFileFromUri(
        context: Context,
        uri: Uri,
        fileName: String? = null,
        isImage: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FileManager", "开始从Uri保存文件 - uri: $uri")
            val actualFileName = fileName ?: getFileNameFromUri(context, uri)
            android.util.Log.d("FileManager", "文件名: $actualFileName")
            
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("FileManager", "❌ 无法打开输入流 - uri: $uri")
                return@withContext null
            }
            
            val result = saveFile(context, inputStream, actualFileName, isImage)
            if (result != null) {
                android.util.Log.d("FileManager", "✅ 文件保存成功 - path: ${result.absolutePath}, size: ${result.length()} bytes")
            } else {
                android.util.Log.e("FileManager", "❌ 文件保存失败")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("FileManager", "❌ 保存文件异常: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 从 Uri 中获取文件名
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/') ?: "unknown_file"
        }
        return result!!
    }
    
    /**
     * 获取本地文件
     */
    fun getLocalFile(context: Context, fileName: String, isImage: Boolean = false): File? {
        val dir = if (isImage) getImagesDir(context) else getFilesDir(context)
        val file = File(dir, fileName)
        return if (file.exists()) file else null
    }
    
    /**
     * 删除文件
     */
    suspend fun deleteFile(context: Context, fileName: String, isImage: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val dir = if (isImage) getImagesDir(context) else getFilesDir(context)
                val file = File(dir, fileName)
                file.delete()
            } catch (e: Exception) {
                false
            }
        }
    
    /**
     * 清理缓存
     */
    suspend fun clearCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheDir = getCacheDir(context)
            cacheDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取缓存大小
     */
    suspend fun getCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        try {
            val cacheDir = getCacheDir(context)
            var size = 0L
            cacheDir.listFiles()?.forEach {
                size += it.length()
            }
            size
        } catch (e: Exception) {
            0L
        }
    }
}

