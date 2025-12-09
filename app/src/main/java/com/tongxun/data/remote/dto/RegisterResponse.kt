package com.tongxun.data.remote.dto

data class RegisterResponse(
    val token: String,
    val user: UserDto,
    val expiresIn: Long
)

