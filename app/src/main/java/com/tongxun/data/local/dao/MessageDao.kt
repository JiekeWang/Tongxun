package com.tongxun.data.local.dao

import androidx.room.*
import com.tongxun.data.local.entity.MessageEntity
import com.tongxun.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Query("""
        SELECT * FROM messages 
        WHERE conversationId = :conversationId 
        AND isRecalled = 0
        ORDER BY timestamp DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getMessages(
        conversationId: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<MessageEntity>
    
    @Query("""
        SELECT * FROM messages 
        WHERE conversationId = :conversationId 
        AND isRecalled = 0
        ORDER BY timestamp ASC
    """)
    fun getMessagesFlow(conversationId: String): Flow<List<MessageEntity>>
    
    @Query("""
        SELECT * FROM messages 
        WHERE conversationId = :conversationId 
        AND timestamp < :beforeTimestamp
        AND isRecalled = 0
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getMessagesBefore(
        conversationId: String,
        beforeTimestamp: Long,
        limit: Int = 20
    ): List<MessageEntity>
    
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND timestamp < :beforeTimestamp")
    suspend fun getMessageCountBefore(conversationId: String, beforeTimestamp: Long): Int
    
    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): MessageEntity?
    
    @Query("""
        SELECT * FROM messages 
        WHERE conversationId = :conversationId 
        AND isRecalled = 0
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLastMessageByConversation(conversationId: String): MessageEntity?
    
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND status != :status")
    suspend fun getUnreadCount(conversationId: String, status: MessageStatus = MessageStatus.READ): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
    
    @Query("UPDATE messages SET isRecalled = 1 WHERE messageId = :messageId")
    suspend fun recallMessage(messageId: String)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)
    
    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
    
    @Query("SELECT messageId FROM messages")
    suspend fun getAllMessageIds(): List<String>
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<MessageEntity>
}

