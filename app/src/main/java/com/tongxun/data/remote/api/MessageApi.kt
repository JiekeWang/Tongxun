package com.tongxun.data.remote.api

import com.tongxun.data.repository.MarkAsReadRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

import retrofit2.http.GET
import retrofit2.http.Query

interface MessageApi {
    
    @POST("messages/{messageId}/recall")
    suspend fun recallMessage(@Path("messageId") messageId: String)
    
    @POST("messages/read")
    suspend fun markAsRead(@Body request: MarkAsReadRequest)
    
    @GET("messages/offline")
    suspend fun getOfflineMessages(@Query("lastMessageTime") lastMessageTime: Long? = null): List<OfflineMessageDto>
    
    @GET("messages/{messageId}/readers")
    suspend fun getMessageReaders(
        @Path("messageId") messageId: String
    ): com.tongxun.data.remote.dto.MessageReadStatsDto
    
    @DELETE("messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: String)
}

data class OfflineMessageDto(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val messageType: String,
    val timestamp: Long,
    val status: String,
    val extra: String?,
    val isRead: Int
)

