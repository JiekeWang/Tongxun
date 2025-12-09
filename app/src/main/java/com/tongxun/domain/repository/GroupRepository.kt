package com.tongxun.domain.repository

import com.tongxun.data.local.entity.GroupEntity
import com.tongxun.data.remote.dto.GroupDto
import com.tongxun.data.remote.dto.GroupMemberDto
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    // 创建群组
    suspend fun createGroup(groupName: String, description: String? = null, memberIds: List<String>? = null): Result<GroupDto>
    
    // 搜索群组
    suspend fun searchGroups(keyword: String): Result<List<GroupDto>>
    
    // 申请加入群组
    suspend fun applyToJoinGroup(groupId: String, message: String? = null): Result<String> // 返回requestId
    
    // 直接加入群组（公开群）
    suspend fun joinGroup(groupId: String): Result<Unit>
    
    // 获取群组信息
    suspend fun getGroupInfo(groupId: String): Result<GroupDto>
    
    // 获取群成员列表
    suspend fun getGroupMembers(groupId: String): Result<List<GroupMemberDto>>
    
    // 添加成员到群组
    suspend fun addMembersToGroup(groupId: String, memberIds: List<String>): Result<Int> // 返回添加的数量
    
    // 退出群组
    suspend fun leaveGroup(groupId: String): Result<Unit>
    
    // 获取群组申请列表（群主/管理员）
    suspend fun getGroupJoinRequests(groupId: String): Result<List<com.tongxun.data.remote.api.GroupJoinRequestDto>>
    
    // 审核申请
    suspend fun approveJoinRequest(groupId: String, requestId: String): Result<Unit>
    suspend fun rejectJoinRequest(groupId: String, requestId: String): Result<Unit>
    
    // 删除群成员（群主或管理员）
    suspend fun removeMember(groupId: String, memberId: String): Result<Unit>
    
    // 解散群组（仅群主）
    suspend fun disbandGroup(groupId: String): Result<Unit>
    
    // 本地数据库操作
    fun getAllGroups(): Flow<List<GroupEntity>>
    suspend fun getGroupById(groupId: String): GroupEntity?
    suspend fun saveGroup(group: GroupEntity)
    suspend fun deleteGroup(groupId: String)
}

