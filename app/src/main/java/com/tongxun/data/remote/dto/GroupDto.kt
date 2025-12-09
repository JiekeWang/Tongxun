package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GroupDto(
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("groupName")
    val groupName: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("ownerId")
    val ownerId: String,
    @SerializedName("memberCount")
    val memberCount: Int,
    @SerializedName("maxMemberCount")
    val maxMemberCount: Int,
    @SerializedName("createdAt")
    val createdAt: Long,
    @SerializedName("members")
    val members: List<GroupMemberDto>? = null
)

data class GroupMemberDto(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("nickname")
    val nickname: String,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("role")
    val role: String // OWNER, ADMIN, MEMBER
)


