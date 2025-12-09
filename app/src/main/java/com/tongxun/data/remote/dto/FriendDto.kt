package com.tongxun.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 好友数据传输对象
 * 用于从服务器获取好友列表
 */
data class FriendDto(
    @SerializedName("friendId")
    val friendId: String,
    @SerializedName("remark")
    val remark: String? = null,
    @SerializedName("groupName")
    val groupName: String? = null,
    @SerializedName("nickname")
    val nickname: String? = null,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("signature")
    val signature: String? = null
) {
    /**
     * 验证数据有效性
     */
    fun isValid(): Boolean {
        return friendId.isNotBlank() && friendId.length <= 128
    }
}

