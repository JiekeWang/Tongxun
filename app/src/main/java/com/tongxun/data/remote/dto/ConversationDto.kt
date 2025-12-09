package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.tongxun.data.local.entity.ConversationType
import java.text.SimpleDateFormat
import java.util.*

data class ConversationDto(
    @SerializedName("conversation_id")
    val conversationId: String,
    @SerializedName("type")
    val type: String, // "SINGLE" or "GROUP"
    @SerializedName("target_id")
    val targetId: String,
    @SerializedName("target_name")
    val targetName: String?,
    @SerializedName("target_avatar")
    val targetAvatar: String?,
    @SerializedName("last_message")
    val lastMessage: String?,
    @SerializedName("last_message_time")
    val lastMessageTime: Long,
    @SerializedName("unread_count")
    val unreadCount: Int = 0,
    @SerializedName("is_top")
    val isTop: Int = 0, // Server sends 0 or 1
    @SerializedName("is_muted")
    val isMuted: Int = 0, // Server sends 0 or 1
    @SerializedName("updated_at")
    val updatedAt: String? = null // Server sends ISO 8601 string like "2025-12-08T04:15:59.000Z"
) {
    fun toEntity(): com.tongxun.data.local.entity.ConversationEntity {
        return com.tongxun.data.local.entity.ConversationEntity(
            conversationId = conversationId,
            type = when (type) {
                "SINGLE" -> ConversationType.SINGLE
                "GROUP" -> ConversationType.GROUP
                else -> ConversationType.SINGLE
            },
            targetId = targetId,
            targetName = targetName ?: "",
            targetAvatar = targetAvatar,
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime,
            unreadCount = unreadCount,
            isTop = isTop == 1, // Convert int (0 or 1) to boolean
            isMuted = isMuted == 1, // Convert int (0 or 1) to boolean
            updatedAt = parseUpdatedAt(updatedAt)
        )
    }
    
    /**
     * 解析 ISO 8601 格式的时间字符串为时间戳（毫秒）
     * 支持格式：2025-12-08T04:15:59.000Z
     */
    private fun parseUpdatedAt(updatedAt: String?): Long {
        if (updatedAt == null || updatedAt.isBlank()) {
            return System.currentTimeMillis()
        }
        
        return try {
            // 尝试使用 java.time.Instant（API 26+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant.parse(updatedAt).toEpochMilli()
            } else {
                // 兼容旧版本，使用 SimpleDateFormat
                // ISO 8601 格式：yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(updatedAt)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // 解析失败，返回当前时间
            System.currentTimeMillis()
        }
    }
}

