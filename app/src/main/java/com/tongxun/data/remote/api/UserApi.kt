package com.tongxun.data.remote.api

import com.tongxun.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface UserApi {
    
    @GET("users/{userId}")
    suspend fun getUserById(@Path("userId") userId: String): UserDto
    
    @GET("users/search")
    suspend fun searchUser(@Query("phone") phone: String? = null, @Query("userId") userId: String? = null): UserDto?
    
    @PUT("users/{userId}")
    suspend fun updateUser(@Path("userId") userId: String, @Body request: UpdateUserRequest): UserDto
}

data class UpdateUserRequest(
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null
)

