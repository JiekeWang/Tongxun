package com.tongxun.data.local.dao

import androidx.room.*
import com.tongxun.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Query("SELECT * FROM conversations ORDER BY isTop DESC, lastMessageTime DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE conversationId = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationEntity?
    
    @Query("SELECT * FROM conversations WHERE conversationId = :conversationId")
    fun getConversationFlow(conversationId: String): Flow<ConversationEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)
    
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)
    
    @Query("UPDATE conversations SET unreadCount = unreadCount + :count WHERE conversationId = :conversationId")
    suspend fun increaseUnreadCount(conversationId: String, count: Int = 1)
    
    @Query("UPDATE conversations SET unreadCount = 0 WHERE conversationId = :conversationId")
    suspend fun clearUnreadCount(conversationId: String)
    
    @Query("UPDATE conversations SET isTop = :isTop WHERE conversationId = :conversationId")
    suspend fun updateTopStatus(conversationId: String, isTop: Boolean)
    
    @Query("UPDATE conversations SET isMuted = :isMuted WHERE conversationId = :conversationId")
    suspend fun updateMutedStatus(conversationId: String, isMuted: Boolean)
    
    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)
    
    @Query("DELETE FROM conversations WHERE conversationId = :conversationId")
    suspend fun deleteConversationById(conversationId: String): Int
    
    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}

