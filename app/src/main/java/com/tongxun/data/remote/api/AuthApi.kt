package com.tongxun.data.remote.api

import com.google.gson.annotations.SerializedName
import com.tongxun.data.remote.dto.LoginRequest
import com.tongxun.data.remote.dto.LoginResponse
import com.tongxun.data.remote.dto.RegisterRequest
import com.tongxun.data.remote.dto.RegisterResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse
    
    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse
    
    @POST("auth/logout")
    suspend fun logout()
}

data class RefreshTokenRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

data class RefreshTokenResponse(
    val token: String,
    val expiresIn: Long
)

