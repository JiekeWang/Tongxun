package com.tongxun.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "friends",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["friendId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["friendId"])]
)
data class FriendEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val friendId: String,
    val remark: String? = null,
    val groupName: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val isBlocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

