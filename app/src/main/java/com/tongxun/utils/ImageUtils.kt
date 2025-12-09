package com.tongxun.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {
    
    /**
     * 压缩图片
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1920,
        maxHeight: Int = 1920,
        quality: Int = 85
    ): File? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var originalBitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        var outputStream: FileOutputStream? = null
        
        try {
            // 打开输入流
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("ImageUtils", "无法打开URI输入流: $uri")
                return@withContext null
            }
            
            // 解码图片
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            inputStream = null
            
            if (originalBitmap == null) {
                android.util.Log.e("ImageUtils", "无法解码图片: $uri")
                return@withContext null
            }
            
            // 处理图片旋转
            rotatedBitmap = rotateImageIfRequired(context, originalBitmap, uri)
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle()
                originalBitmap = null
            }
            
            // 计算缩放比例
            val width = rotatedBitmap.width
            val height = rotatedBitmap.height
            val scale = minOf(
                maxWidth.toFloat() / width,
                maxHeight.toFloat() / height,
                1.0f
            )
            
            scaledBitmap = if (scale < 1.0f) {
                val scaledWidth = (width * scale).toInt()
                val scaledHeight = (height * scale).toInt()
                Bitmap.createScaledBitmap(rotatedBitmap, scaledWidth, scaledHeight, true)
            } else {
                rotatedBitmap
            }
            
            if (scaledBitmap != rotatedBitmap) {
                rotatedBitmap.recycle()
                rotatedBitmap = null
            }
            
            // 保存压缩后的图片
            val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            outputStream = FileOutputStream(outputFile)
            
            val compressSuccess = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            if (!compressSuccess) {
                android.util.Log.e("ImageUtils", "图片压缩失败")
                outputFile.delete()
                return@withContext null
            }
            
            outputStream.flush()
            outputStream.close()
            outputStream = null
            
            scaledBitmap.recycle()
            scaledBitmap = null
            
            android.util.Log.d("ImageUtils", "图片压缩成功: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
            outputFile
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageUtils", "内存不足，无法压缩图片", e)
            null
        } catch (e: SecurityException) {
            android.util.Log.e("ImageUtils", "权限不足，无法访问图片: $uri", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "压缩图片异常: ${e.message}", e)
            null
        } finally {
            // 清理资源
            inputStream?.close()
            originalBitmap?.recycle()
            rotatedBitmap?.recycle()
            scaledBitmap?.recycle()
            try {
                outputStream?.close()
            } catch (e: Exception) {
                // 忽略关闭异常
            }
        }
    }
    
    /**
     * 生成缩略图
     */
    suspend fun generateThumbnail(
        context: Context,
        uri: Uri,
        thumbnailSize: Int = 200
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            
            // 只解码图片尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // 计算缩放比例
            val scale = minOf(
                options.outWidth.toFloat() / thumbnailSize,
                options.outHeight.toFloat() / thumbnailSize
            ).toInt().coerceAtLeast(1)
            
            // 解码缩略图
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            
            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val thumbnail = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()
            
            thumbnail
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 处理图片旋转
     */
    private fun rotateImageIfRequired(
        context: Context,
        bitmap: Bitmap,
        uri: Uri
    ): Bitmap {
        return try {
            val orientation = getImageOrientation(context, uri)
            
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            android.util.Log.w("ImageUtils", "处理图片旋转失败，使用原图", e)
            bitmap
        }
    }
    
    /**
     * 获取图片方向信息
     */
    private fun getImageOrientation(context: Context, uri: Uri): Int {
        return try {
            // 方法1: 如果是 file:// URI，直接使用文件路径
            if (uri.scheme == "file") {
                val filePath = uri.path
                if (!filePath.isNullOrBlank() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    try {
                        val exif = ExifInterface(filePath)
                        exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("ImageUtils", "读取 EXIF 信息失败: ${e.message}", e)
                        ExifInterface.ORIENTATION_NORMAL
                    }
                } else {
                    ExifInterface.ORIENTATION_NORMAL
                }
            } else {
                // 方法2: 使用 InputStream（API 24+）
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    val inputStream = context.contentResolver.openInputStream(uri) ?: return ExifInterface.ORIENTATION_NORMAL
                    try {
                        val exif = ExifInterface(inputStream)
                        exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("ImageUtils", "读取 EXIF 信息失败: ${e.message}", e)
                        ExifInterface.ORIENTATION_NORMAL
                    } finally {
                        try {
                            inputStream.close()
                        } catch (e: Exception) {
                            // 忽略关闭异常
                        }
                    }
                } else {
                    // API 24 以下，尝试从 MediaStore 获取（通常为0，表示不需要旋转）
                    ExifInterface.ORIENTATION_NORMAL
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ImageUtils", "无法读取图片方向信息，使用默认方向: ${e.message}", e)
            ExifInterface.ORIENTATION_NORMAL
        }
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
    
    /**
     * 获取文件大小（MB）
     */
    fun getFileSizeInMB(file: File): Float {
        return file.length() / (1024f * 1024f)
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

