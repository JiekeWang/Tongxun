package com.tongxun.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tongxun.data.model.MessageType
import com.tongxun.data.local.entity.MessageStatus

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["conversationId"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["messageId"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val messageType: MessageType,
    val timestamp: Long,
    val status: MessageStatus,
    val isRecalled: Boolean = false,
    val extra: String? = null // JSON格式的额外数据
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

