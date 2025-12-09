package com.tongxun.data.remote.dto

import com.tongxun.data.model.MessageType

data class MessageDto(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val messageType: MessageType,
    val timestamp: Long,
    val extra: String? = null
)

