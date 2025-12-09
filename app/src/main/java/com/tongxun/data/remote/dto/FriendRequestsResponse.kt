package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FriendRequestsResponse(
    @SerializedName("received")
    val received: List<FriendRequestDto>,
    @SerializedName("sent")
    val sent: List<FriendRequestDto>
)

