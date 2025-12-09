package com.tongxun.domain.repository

import com.tongxun.data.local.entity.MessageEntity
import com.tongxun.data.model.MessageType
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessages(conversationId: String): Flow<List<MessageEntity>>
    suspend fun loadMoreMessages(
        conversationId: String,
        beforeTimestamp: Long,
        limit: Int = 20
    ): Result<List<MessageEntity>>
    suspend fun hasMoreMessages(conversationId: String, beforeTimestamp: Long): Boolean
    suspend fun sendMessage(
        conversationId: String,
        receiverId: String,
        content: String,
        messageType: MessageType,
        extra: String? = null
    ): Result<MessageEntity>
    suspend fun recallMessage(messageId: String)
    suspend fun deleteMessage(messageId: String)
    suspend fun markAsRead(conversationId: String)
    suspend fun fetchOfflineMessages(lastMessageTime: Long? = null): Result<List<MessageEntity>>
    suspend fun getMessageReadStats(messageId: String): Result<com.tongxun.data.remote.dto.MessageReadStatsDto>
}

