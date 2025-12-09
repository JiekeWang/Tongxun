package com.tongxun.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface UploadApi {
    
    @Multipart
    @POST("upload/file")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): UploadResponse
    
    @Multipart
    @POST("upload/chunk")
    suspend fun uploadChunk(
        @Part("fileId") fileId: RequestBody,
        @Part("chunkIndex") chunkIndex: RequestBody,
        @Part("totalChunks") totalChunks: RequestBody,
        @Part("fileName") fileName: RequestBody,
        @Part("fileSize") fileSize: RequestBody,
        @Part("mimeType") mimeType: RequestBody,
        @Part chunk: MultipartBody.Part
    ): ChunkUploadResponse
    
    @POST("upload/merge")
    suspend fun mergeChunks(
        @Body request: MergeChunksRequest
    ): UploadResponse
    
    @Streaming
    @GET("upload/download/{fileId}")
    suspend fun downloadFile(
        @Path("fileId") fileId: String
    ): Response<ResponseBody>
}

data class UploadResponse(
    @SerializedName("fileId")
    val fileId: String,
    @SerializedName("fileUrl")
    val fileUrl: String,
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String?,
    @SerializedName("fileName")
    val fileName: String,
    @SerializedName("fileSize")
    val fileSize: Long,
    @SerializedName("mimeType")
    val mimeType: String
)

data class ChunkUploadResponse(
    val fileId: String,
    val chunkIndex: Int,
    val uploaded: Boolean
)

data class MergeChunksRequest(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

