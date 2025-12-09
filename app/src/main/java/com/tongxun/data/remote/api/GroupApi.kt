package com.tongxun.data.remote.api

import com.google.gson.annotations.SerializedName
import com.tongxun.data.remote.dto.GroupDto
import com.tongxun.data.remote.dto.GroupMemberDto
import com.tongxun.data.remote.dto.MessageReadStatsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GroupApi {

    @POST("groups/create")
    suspend fun createGroup(
        @Body request: CreateGroupRequest
    ): CreateGroupResponse

    @GET("groups/search")
    suspend fun searchGroups(
        @Query("keyword") keyword: String
    ): List<GroupDto>

    @POST("groups/{groupId}/join")
    suspend fun joinGroup(
        @Path("groupId") groupId: String
    ): retrofit2.Response<Unit>

    @POST("groups/{groupId}/apply")
    suspend fun applyToJoinGroup(
        @Path("groupId") groupId: String,
        @Body request: ApplyGroupRequest
    ): ApplyGroupResponse

    @GET("groups/{groupId}/requests")
    suspend fun getGroupJoinRequests(
        @Path("groupId") groupId: String
    ): List<GroupJoinRequestDto>

    @POST("groups/{groupId}/requests/{requestId}/approve")
    suspend fun approveJoinRequest(
        @Path("groupId") groupId: String,
        @Path("requestId") requestId: String
    ): retrofit2.Response<Unit>

    @POST("groups/{groupId}/requests/{requestId}/reject")
    suspend fun rejectJoinRequest(
        @Path("groupId") groupId: String,
        @Path("requestId") requestId: String
    ): retrofit2.Response<Unit>

    @POST("groups/{groupId}/members")
    suspend fun addMembers(
        @Path("groupId") groupId: String,
        @Body request: AddMembersRequest
    ): AddMembersResponse

    @POST("groups/{groupId}/leave")
    suspend fun leaveGroup(
        @Path("groupId") groupId: String
    ): retrofit2.Response<Unit>

    @GET("groups/{groupId}")
    suspend fun getGroupInfo(
        @Path("groupId") groupId: String
    ): GroupDto

    @GET("groups/{groupId}/members")
    suspend fun getGroupMembers(
        @Path("groupId") groupId: String
    ): List<GroupMemberDto>

    @GET("groups/{groupId}/messages/{messageId}/readers")
    suspend fun getMessageReaders(
        @Path("groupId") groupId: String,
        @Path("messageId") messageId: String
    ): MessageReadStatsDto

    @DELETE("groups/{groupId}/members/{memberId}")
    suspend fun removeMember(
        @Path("groupId") groupId: String,
        @Path("memberId") memberId: String
    ): retrofit2.Response<Unit>

    @DELETE("groups/{groupId}")
    suspend fun disbandGroup(
        @Path("groupId") groupId: String
    ): retrofit2.Response<Unit>
}

data class ApplyGroupRequest(
    val message: String? = null
)

data class ApplyGroupResponse(
    val requestId: String,
    val success: Boolean
)

data class GroupJoinRequestDto(
    val requestId: String,
    val userId: String,
    val nickname: String,
    val avatar: String?,
    val message: String?,
    val status: String,
    val createdAt: Long
)

data class AddMembersRequest(
    @SerializedName("memberIds")
    val memberIds: List<String>
)

data class AddMembersResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("addedCount")
    val addedCount: Int
)

data class CreateGroupRequest(
    @com.google.gson.annotations.SerializedName("groupName")
    val groupName: String,
    @com.google.gson.annotations.SerializedName("description")
    val description: String? = null,
    @com.google.gson.annotations.SerializedName("memberIds")
    val memberIds: List<String>? = null
)

data class CreateGroupResponse(
    @com.google.gson.annotations.SerializedName("groupId")
    val groupId: String,
    @com.google.gson.annotations.SerializedName("groupName")
    val groupName: String,
    @com.google.gson.annotations.SerializedName("description")
    val description: String?,
    @com.google.gson.annotations.SerializedName("ownerId")
    val ownerId: String,
    @com.google.gson.annotations.SerializedName("memberCount")
    val memberCount: Int
)

