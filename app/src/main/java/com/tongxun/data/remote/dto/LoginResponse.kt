package com.tongxun.data.remote.dto

data class LoginResponse(
    val token: String,
    val user: UserDto,
    val expiresIn: Long
)

