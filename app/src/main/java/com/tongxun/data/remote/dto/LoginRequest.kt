package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("password")
    val password: String
)

