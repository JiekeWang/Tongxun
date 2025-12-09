package com.tongxun.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.tongxun.data.local.TongxunDatabase
import com.tongxun.data.local.entity.FriendEntity
import com.tongxun.data.remote.api.FriendApi
import com.tongxun.data.remote.dto.FriendDto
import com.tongxun.data.remote.dto.FriendRequestDto
import com.tongxun.data.remote.dto.FriendRequestsResponse
import com.tongxun.data.remote.dto.SendFriendRequestDto
import com.tongxun.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val friendApi: FriendApi,
    private val database: TongxunDatabase,
    private val userRepository: com.tongxun.domain.repository.UserRepository
) : FriendRepository {
    
    private val friendDao = database.friendDao()
    private val syncMutex = Mutex() // 防止并发同步
    
    companion object {
        private const val TAG = "FriendRepository"
        private const val UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        private const val MAX_FRIEND_ID_LENGTH = 128
    }
    
    /**
     * 验证 UUID 格式
     */
    private fun isValidUUID(id: String): Boolean {
        return id.matches(Regex(UUID_PATTERN, RegexOption.IGNORE_CASE))
    }
    
    /**
     * 验证用户ID
     */
    private fun validateUserId(userId: String): Boolean {
        return userId.isNotBlank() && userId.length <= MAX_FRIEND_ID_LENGTH
    }
    
           override fun getFriends(userId: String): Flow<List<FriendEntity>> {
               Log.d(TAG, "getFriends() - userId: ${userId.take(8)}...")
               return friendDao.getFriends(userId)
           }
    
    override suspend fun addFriend(userId: String, friendId: String, message: String?): Result<Unit> {
        // 输入验证
        if (!validateUserId(userId)) {
            Log.w(TAG, "添加好友失败：用户ID无效 - userId: ${userId.take(10)}...")
            return Result.failure(IllegalArgumentException("用户ID格式无效"))
        }
        
        if (!validateUserId(friendId)) {
            Log.w(TAG, "添加好友失败：好友ID无效 - friendId: ${friendId.take(10)}...")
            return Result.failure(IllegalArgumentException("好友ID格式无效"))
        }
        
        if (userId == friendId) {
            Log.w(TAG, "添加好友失败：不能添加自己为好友")
            return Result.failure(IllegalArgumentException("不能添加自己为好友"))
        }
        
        // 验证消息长度（如果提供）
        if (message != null && message.length > 200) {
            Log.w(TAG, "添加好友失败：消息长度超过限制")
            return Result.failure(IllegalArgumentException("消息长度不能超过200个字符"))
        }
        
        return try {
            friendApi.sendFriendRequest(
                SendFriendRequestDto(
                    toUserId = friendId,
                    message = message?.takeIf { it.isNotBlank() }
                )
            )
            Log.d(TAG, "好友请求发送成功 - friendId: ${friendId.take(8)}...")
            Result.success(Unit)
        } catch (e: retrofit2.HttpException) {
            val errorMessage = parseHttpErrorMessage(e, "发送好友请求失败")
            Log.e(TAG, "发送好友请求失败 - HTTP ${e.code()}: $errorMessage")
            Result.failure(Exception(errorMessage))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "发送好友请求失败：网络连接错误", e)
            Result.failure(Exception("网络连接失败，请检查网络设置"))
        } catch (e: Exception) {
            Log.e(TAG, "发送好友请求失败：未知错误", e)
            Result.failure(Exception("发送好友请求失败：${e.message ?: "未知错误"}"))
        }
    }
    
    override suspend fun acceptFriendRequest(requestId: String): Result<Unit> {
        // 输入验证
        if (requestId.isBlank() || !isValidUUID(requestId)) {
            Log.w(TAG, "接受好友请求失败：请求ID格式无效 - requestId: ${requestId.take(10)}...")
            return Result.failure(IllegalArgumentException("请求ID格式无效"))
        }
        
        return try {
            Log.d(TAG, "开始接受好友请求 - requestId: ${requestId.take(8)}...")
            friendApi.acceptFriendRequest(requestId)
            Log.d(TAG, "接受好友请求成功 - requestId: ${requestId.take(8)}...")
            Result.success(Unit)
        } catch (e: retrofit2.HttpException) {
            val errorMessage = parseHttpErrorMessage(e, "接受好友请求失败")
            Log.e(TAG, "接受好友请求失败 - HTTP ${e.code()}: $errorMessage")
            Result.failure(Exception(errorMessage))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "接受好友请求失败：网络连接错误", e)
            Result.failure(Exception("网络连接失败，请检查网络设置"))
        } catch (e: Exception) {
            Log.e(TAG, "接受好友请求失败：未知错误", e)
            Result.failure(Exception("接受好友请求失败：${e.message ?: "未知错误"}"))
        }
    }
    
    override suspend fun rejectFriendRequest(requestId: String): Result<Unit> {
        // 输入验证
        if (requestId.isBlank() || !isValidUUID(requestId)) {
            Log.w(TAG, "拒绝好友请求失败：请求ID格式无效 - requestId: ${requestId.take(10)}...")
            return Result.failure(IllegalArgumentException("请求ID格式无效"))
        }
        
        return try {
            Log.d(TAG, "开始拒绝好友请求 - requestId: ${requestId.take(8)}...")
            friendApi.rejectFriendRequest(requestId)
            Log.d(TAG, "拒绝好友请求成功 - requestId: ${requestId.take(8)}...")
            Result.success(Unit)
        } catch (e: retrofit2.HttpException) {
            val errorMessage = parseHttpErrorMessage(e, "拒绝好友请求失败")
            Log.e(TAG, "拒绝好友请求失败 - HTTP ${e.code()}: $errorMessage")
            Result.failure(Exception(errorMessage))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "拒绝好友请求失败：网络连接错误", e)
            Result.failure(Exception("网络连接失败，请检查网络设置"))
        } catch (e: Exception) {
            Log.e(TAG, "拒绝好友请求失败：未知错误", e)
            Result.failure(Exception("拒绝好友请求失败：${e.message ?: "未知错误"}"))
        }
    }
    
    override suspend fun deleteFriend(userId: String, friendId: String) {
        if (!validateUserId(userId) || !validateUserId(friendId)) {
            Log.w(TAG, "删除好友失败：用户ID或好友ID无效")
            return
        }
        
        try {
            friendDao.deleteFriend(userId, friendId)
            Log.d(TAG, "删除好友成功 - friendId: ${friendId.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "删除好友失败", e)
            throw e
        }
    }
    
    override suspend fun blockFriend(userId: String, friendId: String, isBlocked: Boolean) {
        if (!validateUserId(userId) || !validateUserId(friendId)) {
            Log.w(TAG, "拉黑/取消拉黑好友失败：用户ID或好友ID无效")
            return
        }
        
        try {
            friendDao.updateBlockStatus(userId, friendId, isBlocked)
            Log.d(TAG, "好友拉黑状态更新成功 - friendId: ${friendId.take(8)}..., isBlocked: $isBlocked")
        } catch (e: Exception) {
            Log.e(TAG, "更新好友拉黑状态失败", e)
            throw e
        }
    }
    
    override suspend fun getFriendRequests(): Result<com.tongxun.data.remote.dto.FriendRequestsResponse> {
        return try {
            val response = friendApi.getFriendRequests()
            Log.d(TAG, "获取好友请求列表成功 - 收到: ${response.received.size}, 已发送: ${response.sent.size}")
            Result.success(response)
        } catch (e: retrofit2.HttpException) {
            val errorMessage = parseHttpErrorMessage(e, "获取好友请求列表失败")
            Log.e(TAG, "获取好友请求列表失败 - HTTP ${e.code()}: $errorMessage")
            Result.failure(Exception(errorMessage))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "获取好友请求列表失败：网络连接错误", e)
            Result.failure(Exception("网络连接失败，请检查网络设置"))
        } catch (e: Exception) {
            Log.e(TAG, "获取好友请求列表失败：未知错误", e)
            Result.failure(e)
        }
    }
    
    override suspend fun syncFriendsFromServer(userId: String): Result<Unit> {
        // 输入验证
        if (!validateUserId(userId)) {
            Log.w(TAG, "同步好友列表失败：用户ID无效")
            return Result.failure(IllegalArgumentException("用户ID格式无效"))
        }
        
        // 使用互斥锁防止并发同步
        return syncMutex.withLock {
            try {
                Log.d(TAG, "开始从服务器同步好友列表 - userId: ${userId.take(8)}...")
                
                // 从服务器获取好友列表
                val friendsFromServer = friendApi.getFriends()
                Log.d(TAG, "从服务器获取到 ${friendsFromServer.size} 个好友")
                
                // 过滤无效数据并转换为本地实体
                val validFriends = friendsFromServer.filter { it.isValid() }
                if (validFriends.size < friendsFromServer.size) {
                    Log.w(TAG, "过滤了 ${friendsFromServer.size - validFriends.size} 个无效好友数据")
                }
                
                // 先确保所有好友的 UserEntity 存在于数据库中（满足外键约束）
                Log.d(TAG, "开始确保好友的用户信息存在 - 共 ${validFriends.size} 个好友")
                validFriends.forEach { friendDto ->
                    try {
                        // 尝试获取用户信息，如果不存在会自动从服务器获取并保存
                        userRepository.getUserById(friendDto.friendId)
                        Log.d(TAG, "好友用户信息已存在或已获取 - friendId: ${friendDto.friendId.take(8)}...")
                    } catch (e: Exception) {
                        Log.w(TAG, "获取好友用户信息失败，但继续处理 - friendId: ${friendDto.friendId.take(8)}..., error: ${e.message}")
                        // 如果获取失败，尝试从 FriendDto 创建基本的 UserEntity
                        try {
                            val basicUserEntity = com.tongxun.data.local.entity.UserEntity(
                                userId = friendDto.friendId,
                                phoneNumber = "", // 好友信息中没有手机号
                                nickname = friendDto.nickname ?: "",
                                avatar = friendDto.avatar,
                                signature = friendDto.signature
                            )
                            database.userDao().insertUser(basicUserEntity)
                            Log.d(TAG, "已创建基本的好友用户信息 - friendId: ${friendDto.friendId.take(8)}...")
                        } catch (insertError: Exception) {
                            Log.e(TAG, "创建好友用户信息失败 - friendId: ${friendDto.friendId.take(8)}...", insertError)
                        }
                    }
                }
                
                val friendEntities = validFriends.map { friendDto ->
                    FriendEntity(
                        userId = userId,
                        friendId = friendDto.friendId,
                        remark = friendDto.remark?.takeIf { it.length <= 100 },
                        groupName = friendDto.groupName?.takeIf { it.length <= 50 },
                        nickname = friendDto.nickname?.takeIf { it.length <= 50 },
                        avatar = friendDto.avatar?.takeIf { it.length <= 500 },
                        isBlocked = false,
                        createdAt = System.currentTimeMillis()
                    )
                }
                
                // 使用事务确保数据一致性
                database.withTransaction {
                    // 删除当前用户的所有好友
                    friendDao.deleteAllFriends(userId)
                    Log.d(TAG, "已清空旧的好友记录")
                    
                    // 批量插入新的好友列表
                    if (friendEntities.isNotEmpty()) {
                        // 分批插入以避免一次性插入过多数据
                        friendEntities.chunked(100).forEach { chunk ->
                            friendDao.insertFriends(chunk)
                        }
                        Log.d(TAG, "✅ 好友列表数据库更新完成 - 共 ${friendEntities.size} 个好友")
                    } else {
                        Log.w(TAG, "⚠️ 服务器返回的好友列表为空")
                    }
                }
                
                Log.d(TAG, "✅✅✅ 好友列表同步成功 - 共 ${friendEntities.size} 个好友")
                Result.success(Unit)
            } catch (e: retrofit2.HttpException) {
                val errorMessage = parseHttpErrorMessage(e, "同步好友列表失败")
                Log.e(TAG, "同步好友列表失败 - HTTP ${e.code()}: $errorMessage")
                Result.failure(Exception(errorMessage))
            } catch (e: java.io.IOException) {
                Log.e(TAG, "同步好友列表失败：网络连接错误", e)
                Result.failure(Exception("网络连接失败，请检查网络设置"))
            } catch (e: Exception) {
                Log.e(TAG, "同步好友列表失败：未知错误", e)
                Result.failure(Exception("同步好友列表失败：${e.message ?: "未知错误"}"))
            }
        }
    }
    
    /**
     * 解析HTTP错误消息
     */
    private fun parseHttpErrorMessage(e: retrofit2.HttpException, defaultMessage: String): String {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val jsonObject = com.google.gson.Gson().fromJson(errorBody, com.google.gson.JsonObject::class.java)
                when {
                    jsonObject.has("error") -> jsonObject.get("error").asString
                    jsonObject.has("errors") -> {
                        val errorsArray = jsonObject.getAsJsonArray("errors")
                        if (errorsArray.size() > 0) {
                            errorsArray[0].asJsonObject.get("msg")?.asString ?: defaultMessage
                        } else {
                            defaultMessage
                        }
                    }
                    else -> defaultMessage
                }
            } else {
                defaultMessage
            }
        } catch (parseError: Exception) {
            Log.w(TAG, "解析HTTP错误消息失败", parseError)
            "$defaultMessage (HTTP ${e.code()})"
        }
    }
}

