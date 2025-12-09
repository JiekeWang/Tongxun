package com.tongxun.data.remote.api

import com.tongxun.data.remote.dto.FriendDto
import com.tongxun.data.remote.dto.FriendRequestDto
import com.tongxun.data.remote.dto.FriendRequestsResponse
import com.tongxun.data.remote.dto.SendFriendRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FriendApi {

    @POST("friends/request")
    suspend fun sendFriendRequest(@Body request: SendFriendRequestDto)

    @POST("friends/accept")
    suspend fun acceptFriendRequest(@Query("requestId") requestId: String)

    @POST("friends/reject")
    suspend fun rejectFriendRequest(@Query("requestId") requestId: String)

    @GET("friends")
    suspend fun getFriends(): List<FriendDto>

    @GET("friends/requests")
    suspend fun getFriendRequests(): FriendRequestsResponse
}

