package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FriendRequestDto(
    @SerializedName("requestId")
    val requestId: String,
    @SerializedName("fromUserId")
    val fromUserId: String,
    @SerializedName("toUserId")
    val toUserId: String,
    @SerializedName("status")
    val status: FriendRequestStatus,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("createdAt")
    val createdAt: Long,
    @SerializedName("nickname")
    val nickname: String? = null,
    @SerializedName("avatar")
    val avatar: String? = null
)

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

