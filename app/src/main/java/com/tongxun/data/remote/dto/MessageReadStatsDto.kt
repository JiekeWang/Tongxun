package com.tongxun.data.remote.dto

data class MessageReadStatsDto(
    val messageId: String,
    val readCount: Int,
    val totalCount: Int,
    val readers: List<ReaderInfo>
)

data class ReaderInfo(
    val userId: String,
    val nickname: String,
    val avatar: String?,
    val readAt: Long
)

