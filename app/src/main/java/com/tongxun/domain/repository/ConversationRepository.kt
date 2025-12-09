package com.tongxun.domain.repository

import com.tongxun.data.local.entity.ConversationEntity
import com.tongxun.data.local.entity.ConversationType
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationById(conversationId: String): ConversationEntity?
    suspend fun getOrCreateConversation(targetId: String, type: ConversationType): ConversationEntity
    suspend fun updateConversation(conversation: ConversationEntity)
    suspend fun deleteConversation(conversationId: String)
    suspend fun setTop(conversationId: String, isTop: Boolean)
    suspend fun setMuted(conversationId: String, isMuted: Boolean)
    suspend fun clearUnreadCount(conversationId: String)
    suspend fun syncConversationsFromServer(): Result<Unit>
}

