package com.tongxun.domain.repository

import com.tongxun.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUserById(userId: String): UserEntity?
    suspend fun searchUser(phone: String? = null, userId: String? = null): UserEntity?
    fun getUserByIdFlow(userId: String): Flow<UserEntity?>
    suspend fun updateUser(user: UserEntity)
}

