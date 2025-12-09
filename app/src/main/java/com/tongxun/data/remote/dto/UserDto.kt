package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("nickname")
    val nickname: String,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("signature")
    val signature: String? = null
)

