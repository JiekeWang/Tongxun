package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("nickname")
    val nickname: String
)

