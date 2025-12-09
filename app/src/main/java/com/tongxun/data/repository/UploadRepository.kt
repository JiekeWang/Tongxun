package com.tongxun.data.repository

import android.content.Context
import android.net.Uri
import com.tongxun.data.remote.api.UploadApi
import com.tongxun.utils.FileManager
import com.tongxun.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepository @Inject constructor(
    private val uploadApi: UploadApi,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    
    /**
     * 上传文件（带进度）
     */
    suspend fun uploadFile(
        file: File,
        onProgress: (Int) -> Unit = {}
    ): Result<com.tongxun.data.remote.api.UploadResponse> {
        return try {
            withContext(Dispatchers.IO) {
                val requestFile = file.asRequestBody(
                    getMimeType(file.name).toMediaTypeOrNull()
                )
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                val response = uploadApi.uploadFile(body)
                onProgress(100)
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 上传图片（压缩后上传）
     */
    suspend fun uploadImage(
        uri: Uri,
        maxWidth: Int = 1920,
        maxHeight: Int = 1920
    ): Result<com.tongxun.data.remote.api.UploadResponse> {
        return try {
            withContext(Dispatchers.IO) {
                // 验证 URI 不为空
                if (uri == Uri.EMPTY) {
                    return@withContext Result.failure(Exception("图片 URI 无效"))
                }
                
                // 压缩图片
                val compressedFile = ImageUtils.compressImage(
                    context,
                    uri,
                    maxWidth,
                    maxHeight
                ) ?: return@withContext Result.failure(Exception("图片压缩失败"))
                
                // 验证压缩后的文件存在
                if (!compressedFile.exists()) {
                    return@withContext Result.failure(Exception("压缩后的文件不存在"))
                }
                
                // 上传文件
                val mediaType = "image/jpeg".toMediaTypeOrNull() ?: return@withContext Result.failure(Exception("无法创建媒体类型"))
                val requestFile = compressedFile.asRequestBody(mediaType)
                val body = MultipartBody.Part.createFormData("file", compressedFile.name, requestFile)
                
                android.util.Log.d("UploadRepository", "开始调用 uploadApi.uploadFile()")
                
                try {
                    val response = uploadApi.uploadFile(body)
                    
                    // 详细记录响应数据
                    android.util.Log.d("UploadRepository", "✅ 上传API调用成功")
                    android.util.Log.d("UploadRepository", "响应对象类型: ${response.javaClass.simpleName}")
                    android.util.Log.d("UploadRepository", "响应 - fileId: '${response.fileId}'")
                    android.util.Log.d("UploadRepository", "响应 - fileUrl: '${response.fileUrl}' (是否为空: ${response.fileUrl.isBlank()}, 长度: ${response.fileUrl.length})")
                    android.util.Log.d("UploadRepository", "响应 - fileName: '${response.fileName}'")
                    android.util.Log.d("UploadRepository", "响应 - fileSize: ${response.fileSize}")
                    android.util.Log.d("UploadRepository", "响应 - mimeType: '${response.mimeType}'")
                    
                    // 验证响应数据
                    if (response.fileUrl.isNullOrBlank()) {
                        android.util.Log.e("UploadRepository", "❌❌❌ 服务器返回的fileUrl为空或null")
                        android.util.Log.e("UploadRepository", "完整响应对象详情:")
                        android.util.Log.e("UploadRepository", "  - fileId: '${response.fileId}'")
                        android.util.Log.e("UploadRepository", "  - fileUrl: '${response.fileUrl}'")
                        android.util.Log.e("UploadRepository", "  - fileName: '${response.fileName}'")
                        android.util.Log.e("UploadRepository", "  - fileSize: ${response.fileSize}")
                        android.util.Log.e("UploadRepository", "  - mimeType: '${response.mimeType}'")
                        android.util.Log.e("UploadRepository", "  - thumbnailUrl: '${response.thumbnailUrl}'")
                        return@withContext Result.failure(Exception("服务器返回的文件URL为空"))
                    }
                    
                    android.util.Log.d("UploadRepository", "✅✅✅ fileUrl验证通过: '${response.fileUrl}'")
                    
                    // 清理临时文件
                    try {
                        compressedFile.delete()
                    } catch (e: Exception) {
                        // 忽略删除失败
                        android.util.Log.w("UploadRepository", "删除临时文件失败", e)
                    }
                    
                    android.util.Log.d("UploadRepository", "✅ 上传完成，返回结果 - fileUrl: '${response.fileUrl}'")
                    Result.success(response)
                } catch (e: Exception) {
                    android.util.Log.e("UploadRepository", "❌ 上传API调用异常", e)
                    throw e
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UploadRepository", "上传图片异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 分片上传大文件
     */
    suspend fun uploadFileInChunks(
        file: File,
        chunkSize: Int = 2 * 1024 * 1024, // 2MB per chunk
        onProgress: (Int) -> Unit = {}
    ): Result<com.tongxun.data.remote.api.UploadResponse> {
        return try {
            withContext(Dispatchers.IO) {
                val fileId = java.util.UUID.randomUUID().toString()
                val totalSize = file.length()
                val totalChunks = ((totalSize + chunkSize - 1) / chunkSize).toInt()
                val mimeType = getMimeType(file.name)
                
                // 上传每个分片
                for (i in 0 until totalChunks) {
                    val start = i * chunkSize.toLong()
                    val end = minOf(start + chunkSize, totalSize)
                    val chunkData = file.readBytes(start.toInt(), (end - start).toInt())
                    
                    val chunkFile = File(context.cacheDir, "chunk_$i")
                    chunkFile.writeBytes(chunkData)
                    
                    val requestFile = chunkFile.asRequestBody(mimeType.toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("chunk", chunkFile.name, requestFile)
                    
                    val fileIdBody = fileId.toRequestBody("text/plain".toMediaTypeOrNull())
                    val chunkIndexBody = i.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val totalChunksBody = totalChunks.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val fileNameBody = file.name.toRequestBody("text/plain".toMediaTypeOrNull())
                    val fileSizeBody = totalSize.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val mimeTypeBody = mimeType.toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    uploadApi.uploadChunk(
                        fileIdBody,
                        chunkIndexBody,
                        totalChunksBody,
                        fileNameBody,
                        fileSizeBody,
                        mimeTypeBody,
                        body
                    )
                    
                    chunkFile.delete()
                    onProgress(((i + 1) * 100 / totalChunks))
                }
                
                // 合并分片
                val mergeRequest = com.tongxun.data.remote.api.MergeChunksRequest(
                    fileId = fileId,
                    fileName = file.name,
                    fileSize = totalSize,
                    mimeType = mimeType
                )
                
                val response = uploadApi.mergeChunks(mergeRequest)
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 下载文件（通过 fileId）
     */
    suspend fun downloadFile(
        fileId: String,
        fileName: String,
        isImage: Boolean = false
    ): Result<File> {
        return try {
            withContext(Dispatchers.IO) {
                val response = uploadApi.downloadFile(fileId)
                val body = response.body() ?: return@withContext Result.failure(Exception("下载失败"))
                
                val file = FileManager.saveFile(
                    context,
                    body.byteStream(),
                    fileName,
                    isImage
                ) ?: return@withContext Result.failure(Exception("保存文件失败"))
                
                Result.success(file)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 通过文件 URL 直接下载文件
     * @param fileUrl 文件 URL，如 "/uploads/audio/xxx.m4a"
     */
    suspend fun downloadFileByUrl(
        fileUrl: String,
        fileName: String,
        isImage: Boolean = false
    ): Result<File> {
        return try {
            withContext(Dispatchers.IO) {
                // 构建完整的 URL
                // BASE_URL 是 "http://47.116.197.230:3000/api/"
                // fileUrl 是 "/uploads/audio/xxx.m4a"
                // 需要去掉 BASE_URL 中的 "/api/" 部分，然后拼接 fileUrl
                val baseUrl = com.tongxun.data.remote.NetworkModule.BASE_URL
                val serverBase = baseUrl.removeSuffix("/api/").removeSuffix("/api")
                val fullUrl = if (fileUrl.startsWith("/")) {
                    "$serverBase$fileUrl"
                } else {
                    "$serverBase/$fileUrl"
                }
                
                android.util.Log.d("UploadRepository", "开始通过 URL 下载文件: $fullUrl")
                
                // 使用 OkHttp 下载文件
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载失败: HTTP ${response.code}"))
                }
                
                val body = response.body ?: return@withContext Result.failure(Exception("响应体为空"))
                
                val file = FileManager.saveFile(
                    context,
                    body.byteStream(),
                    fileName,
                    isImage
                ) ?: return@withContext Result.failure(Exception("保存文件失败"))
                
                android.util.Log.d("UploadRepository", "✅ 文件下载成功: ${file.absolutePath}")
                Result.success(file)
            }
        } catch (e: Exception) {
            android.util.Log.e("UploadRepository", "❌ 通过 URL 下载文件失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }
}

private fun File.readBytes(start: Int, length: Int): ByteArray {
    val buffer = ByteArray(length)
    inputStream().use { input ->
        input.skip(start.toLong())
        input.read(buffer)
    }
    return buffer
}

