package com.tongxun.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val groupName: String,
    val avatar: String? = null,
    val description: String? = null,
    val ownerId: String,
    val memberCount: Int = 0,
    val maxMemberCount: Int = 500,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

