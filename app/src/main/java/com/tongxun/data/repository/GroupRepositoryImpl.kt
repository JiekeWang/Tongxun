package com.tongxun.data.repository

import android.util.Log
import com.tongxun.data.local.TongxunDatabase
import com.tongxun.data.local.entity.GroupEntity
import com.tongxun.data.remote.api.AddMembersRequest
import com.tongxun.data.remote.api.ApplyGroupRequest
import com.tongxun.data.remote.api.GroupApi
import com.tongxun.data.remote.dto.GroupDto
import com.tongxun.data.remote.dto.GroupMemberDto
import com.tongxun.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupApi: GroupApi,
    private val database: TongxunDatabase
) : GroupRepository {
    
    private val TAG = "GroupRepository"
    private val groupDao = database.groupDao()
    
    override suspend fun createGroup(groupName: String, description: String?, memberIds: List<String>?): Result<GroupDto> {
        return try {
            Log.d(TAG, "创建群组 - groupName: $groupName, description: $description, memberIds: ${memberIds?.joinToString(", ") ?: "无"}")
            val request = com.tongxun.data.remote.api.CreateGroupRequest(
                groupName = groupName,
                description = description,
                memberIds = memberIds
            )
            Log.d(TAG, "创建群组请求对象: groupName=${request.groupName}, memberIds=${request.memberIds?.size ?: 0}")
            val response = groupApi.createGroup(request)
            Log.d(TAG, "创建群组成功 - groupId: ${response.groupId}, groupName: ${response.groupName}, memberCount: ${response.memberCount}")
            
            // 验证响应数据
            if (response.groupId.isBlank()) {
                Log.e(TAG, "服务器返回的 groupId 为空")
                return Result.failure(Exception("服务器返回数据无效：groupId 为空"))
            }
            
            // 使用响应数据直接创建 GroupDto，不需要再调用 getGroupInfo
            val groupDto = GroupDto(
                groupId = response.groupId,
                groupName = response.groupName,
                description = response.description,
                avatar = null,
                ownerId = response.ownerId,
                memberCount = response.memberCount,
                maxMemberCount = 500,
                createdAt = System.currentTimeMillis()
            )
            saveGroupInfoToLocal(groupDto)
            
            Result.success(groupDto)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "创建群组失败 - HTTP ${e.code()}: $errorBody", e)
            
            // 尝试解析错误信息
            val errorMessage = if (errorBody != null) {
                try {
                    val gson = com.google.gson.Gson()
                    val jsonObject = gson.fromJson(errorBody, com.google.gson.JsonObject::class.java)
                    when {
                        jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive -> {
                            jsonObject.get("error").asString
                        }
                        jsonObject.has("errors") && jsonObject.get("errors").isJsonArray -> {
                            val errorsArray = jsonObject.getAsJsonArray("errors")
                            if (errorsArray.size() > 0) {
                                val firstError = errorsArray[0].asJsonObject
                                firstError.get("msg")?.asString ?: firstError.get("message")?.asString ?: errorBody
                            } else {
                                errorBody
                            }
                        }
                        else -> errorBody
                    }
                } catch (parseEx: Exception) {
                    Log.w(TAG, "解析错误响应失败", parseEx)
                    "HTTP ${e.code()}: $errorBody"
                }
            } else {
                "HTTP ${e.code()}: ${e.message()}"
            }
            
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG, "创建群组失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun searchGroups(keyword: String): Result<List<GroupDto>> {
        return try {
            Log.d(TAG, "搜索群组 - keyword: $keyword")
            val groups = groupApi.searchGroups(keyword)
            Log.d(TAG, "搜索到 ${groups.size} 个群组")
            Result.success(groups)
        } catch (e: Exception) {
            Log.e(TAG, "搜索群组失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun applyToJoinGroup(groupId: String, message: String?): Result<String> {
        return try {
            Log.d(TAG, "申请加入群组 - groupId: $groupId, message: $message")
            val response = groupApi.applyToJoinGroup(groupId, ApplyGroupRequest(message))
            Log.d(TAG, "申请成功 - requestId: ${response.requestId}")
            Result.success(response.requestId)
        } catch (e: Exception) {
            Log.e(TAG, "申请加入群组失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun joinGroup(groupId: String): Result<Unit> {
        return try {
            Log.d(TAG, "加入群组 - groupId: $groupId")
            groupApi.joinGroup(groupId)
            // 加入成功后，获取群组信息并保存到本地
            val groupInfo = groupApi.getGroupInfo(groupId)
            saveGroupInfoToLocal(groupInfo)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "加入群组失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getGroupInfo(groupId: String): Result<GroupDto> {
        return try {
            Log.d(TAG, "获取群组信息 - groupId: $groupId")
            val groupInfo = groupApi.getGroupInfo(groupId)
            saveGroupInfoToLocal(groupInfo)
            Result.success(groupInfo)
        } catch (e: Exception) {
            Log.e(TAG, "获取群组信息失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getGroupMembers(groupId: String): Result<List<GroupMemberDto>> {
        return try {
            Log.d(TAG, "获取群成员列表 - groupId: $groupId")
            val members = groupApi.getGroupMembers(groupId)
            Result.success(members)
        } catch (e: Exception) {
            Log.e(TAG, "获取群成员列表失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun addMembersToGroup(groupId: String, memberIds: List<String>): Result<Int> {
        return try {
            Log.e(TAG, "🔥🔥🔥 添加成员到群组 - groupId: $groupId, memberIds: ${memberIds.size} 个")
            Log.e(TAG, "🔥🔥🔥 memberIds 内容: $memberIds")
            
            // 验证 memberIds 不为空
            if (memberIds.isEmpty()) {
                Log.e(TAG, "❌❌❌ memberIds 为空，不允许添加成员")
                return Result.failure(Exception("成员ID列表不能为空"))
            }
            
            // 验证 memberIds 中没有空字符串或空白字符串
            val validMemberIds = memberIds.filter { it.isNotBlank() }
            if (validMemberIds.isEmpty()) {
                Log.e(TAG, "❌❌❌ memberIds 中所有ID都无效（空字符串）")
                return Result.failure(Exception("成员ID列表不能为空"))
            }
            
            if (validMemberIds.size != memberIds.size) {
                Log.w(TAG, "⚠️ memberIds 中有无效ID，已过滤 - 原始: ${memberIds.size}, 有效: ${validMemberIds.size}")
            }
            
            Log.e(TAG, "🔥🔥🔥 准备调用API添加成员 - 有效memberIds: $validMemberIds")
            val request = AddMembersRequest(validMemberIds)
            Log.e(TAG, "🔥🔥🔥 AddMembersRequest 对象: memberIds=${request.memberIds}, size=${request.memberIds.size}")
            val response = groupApi.addMembers(groupId, request)
            Log.e(TAG, "✅✅✅ 添加成功 - response.success=${response.success}, response.addedCount=${response.addedCount}")
            Log.e(TAG, "✅✅✅ 请求的成员数: ${validMemberIds.size}, 实际添加的成员数: ${response.addedCount}")
            
            // 如果 addedCount 为 0，说明所有用户都已经是成员了
            if (response.addedCount == 0) {
                Log.w(TAG, "⚠️ addedCount 为 0，可能所有用户都已经是成员")
            }
            
            Result.success(response.addedCount)
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ 添加成员失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun leaveGroup(groupId: String): Result<Unit> {
        return try {
            Log.d(TAG, "退出群组 - groupId: $groupId")
            val response = groupApi.leaveGroup(groupId)
            if (response.isSuccessful) {
                // 从本地删除群组
                deleteGroup(groupId)
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "退出群组失败 - HTTP ${response.code()}: $errorBody")
                Result.failure(Exception("退出失败: ${errorBody ?: response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "退出群组失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getGroupJoinRequests(groupId: String): Result<List<com.tongxun.data.remote.api.GroupJoinRequestDto>> {
        return try {
            Log.d(TAG, "获取群组申请列表 - groupId: $groupId")
            val requests = groupApi.getGroupJoinRequests(groupId)
            Result.success(requests)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "获取群组申请列表失败 - HTTP ${e.code()}: $errorBody")
            Result.failure(Exception("获取申请列表失败: ${errorBody ?: e.message()}"))
        } catch (e: Exception) {
            Log.e(TAG, "获取群组申请列表失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun approveJoinRequest(groupId: String, requestId: String): Result<Unit> {
        return try {
            Log.d(TAG, "批准申请 - groupId: $groupId, requestId: $requestId")
            val response = groupApi.approveJoinRequest(groupId, requestId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "批准申请失败 - HTTP ${response.code()}: $errorBody")
                Result.failure(Exception("批准失败: ${errorBody ?: response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "批准申请失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun rejectJoinRequest(groupId: String, requestId: String): Result<Unit> {
        return try {
            Log.d(TAG, "拒绝申请 - groupId: $groupId, requestId: $requestId")
            val response = groupApi.rejectJoinRequest(groupId, requestId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "拒绝申请失败 - HTTP ${response.code()}: $errorBody")
                Result.failure(Exception("拒绝失败: ${errorBody ?: response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "拒绝申请失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun removeMember(groupId: String, memberId: String): Result<Unit> {
        return try {
            Log.d(TAG, "删除群成员 - groupId: $groupId, memberId: $memberId")
            val response = groupApi.removeMember(groupId, memberId)
            if (response.isSuccessful) {
                // 刷新群组信息
                getGroupInfo(groupId)
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "删除成员失败 - HTTP ${response.code()}: $errorBody")
                Result.failure(Exception("删除成员失败: ${errorBody ?: response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除成员失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun disbandGroup(groupId: String): Result<Unit> {
        return try {
            Log.d(TAG, "解散群组 - groupId: $groupId")
            val response = groupApi.disbandGroup(groupId)
            if (response.isSuccessful) {
                // 从本地删除群组
                deleteGroup(groupId)
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "解散群组失败 - HTTP ${response.code()}: $errorBody")
                Result.failure(Exception("解散群组失败: ${errorBody ?: response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解散群组失败", e)
            Result.failure(e)
        }
    }
    
    override fun getAllGroups(): Flow<List<GroupEntity>> {
        return groupDao.getAllGroups()
    }
    
    override suspend fun getGroupById(groupId: String): GroupEntity? {
        return groupDao.getGroupById(groupId)
    }
    
    override suspend fun saveGroup(group: GroupEntity) {
        groupDao.insertGroup(group)
    }
    
    override suspend fun deleteGroup(groupId: String) {
        groupDao.deleteGroupById(groupId)
    }
    
    private suspend fun saveGroupInfoToLocal(groupDto: GroupDto) {
        try {
            // 🔥 关键修复：检查必要字段是否为 null
            if (groupDto.groupId.isBlank()) {
                Log.e(TAG, "❌❌❌ 保存群组信息失败 - groupId为空或null, groupDto: $groupDto")
                return
            }
            
            val groupEntity = GroupEntity(
                groupId = groupDto.groupId,
                groupName = groupDto.groupName,
                avatar = groupDto.avatar,
                description = groupDto.description,
                ownerId = groupDto.ownerId,
                memberCount = groupDto.memberCount,
                maxMemberCount = groupDto.maxMemberCount,
                createdAt = groupDto.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            groupDao.insertGroup(groupEntity)
            Log.d(TAG, "群组信息已保存到本地 - groupId: ${groupDto.groupId}, groupName: ${groupDto.groupName}")
        } catch (e: Exception) {
            Log.e(TAG, "保存群组信息到本地失败 - groupDto: $groupDto", e)
            e.printStackTrace()
        }
    }
}

