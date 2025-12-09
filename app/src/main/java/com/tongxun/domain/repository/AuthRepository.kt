package com.tongxun.domain.repository

import com.tongxun.data.local.entity.UserEntity

interface AuthRepository {
    suspend fun login(phoneNumber: String, password: String): Result<UserEntity>
    suspend fun register(phoneNumber: String, password: String, nickname: String): Result<UserEntity>
    suspend fun logout()
    suspend fun refreshToken(): Result<String>
    suspend fun checkAutoLogin(): Result<UserEntity>
    fun getCurrentUser(): UserEntity?
    fun isLoggedIn(): Boolean
    fun saveToken(token: String)
    fun getToken(): String?
    fun saveRefreshToken(token: String)
    fun getRefreshToken(): String?
}

