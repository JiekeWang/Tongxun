package com.tongxun.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["lastMessageTime"], orders = [Index.Order.DESC])]
)
data class ConversationEntity(
    @PrimaryKey
    val conversationId: String,
    val type: ConversationType,
    val targetId: String, // 对方用户ID或群组ID
    val targetName: String,
    val targetAvatar: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isTop: Boolean = false,
    val isMuted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ConversationType {
    SINGLE, // 单聊
    GROUP   // 群聊
}

