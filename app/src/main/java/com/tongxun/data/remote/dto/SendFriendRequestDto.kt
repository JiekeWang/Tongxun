package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 发送好友请求的请求体
 */
data class SendFriendRequestDto(
    @SerializedName("toUserId")
    val toUserId: String,
    @SerializedName("message")
    val message: String? = null
)

