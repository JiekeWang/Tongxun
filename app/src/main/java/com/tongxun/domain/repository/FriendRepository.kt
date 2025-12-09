package com.tongxun.domain.repository

import com.tongxun.data.local.entity.FriendEntity
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun getFriends(userId: String): Flow<List<FriendEntity>>
    suspend fun addFriend(userId: String, friendId: String, message: String? = null): Result<Unit>
    suspend fun acceptFriendRequest(requestId: String): Result<Unit>
    suspend fun rejectFriendRequest(requestId: String): Result<Unit>
    suspend fun deleteFriend(userId: String, friendId: String)
    suspend fun blockFriend(userId: String, friendId: String, isBlocked: Boolean)
    suspend fun getFriendRequests(): Result<com.tongxun.data.remote.dto.FriendRequestsResponse>
    suspend fun syncFriendsFromServer(userId: String): Result<Unit>
}

