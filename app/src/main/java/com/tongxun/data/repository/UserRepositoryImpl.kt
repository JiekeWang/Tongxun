package com.tongxun.data.repository

import com.tongxun.data.local.TongxunDatabase
import com.tongxun.data.local.entity.UserEntity
import com.tongxun.data.remote.api.UserApi
import com.tongxun.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val database: TongxunDatabase
) : UserRepository {
    
    private val userDao = database.userDao()
    
    override suspend fun getUserById(userId: String): UserEntity? {
        android.util.Log.e("UserRepositoryImpl", "🔥🔥🔥 getUserById() 被调用 - userId: ${userId.take(8)}...")
        
        // 先查本地
        val localUser = userDao.getUserById(userId)
        if (localUser != null) {
            android.util.Log.d("UserRepositoryImpl", "✅ 从本地数据库获取用户信息 - userId: ${userId.take(8)}..., nickname: ${localUser.nickname}, avatar: ${localUser.avatar?.take(20)}...")
            return localUser
        }
        
        android.util.Log.w("UserRepositoryImpl", "⚠️ 本地数据库中没有用户信息，从服务器获取 - userId: ${userId.take(8)}...")
        
        // 再查远程
        return try {
            android.util.Log.d("UserRepositoryImpl", "开始从服务器获取用户信息 - userId: ${userId.take(8)}...")
            val remoteUser = userApi.getUserById(userId)
            android.util.Log.d("UserRepositoryImpl", "✅ 从服务器获取用户信息成功 - userId: ${remoteUser.userId.take(8)}..., nickname: ${remoteUser.nickname}, avatar: ${remoteUser.avatar?.take(20)}...")
            
            val userEntity = UserEntity(
                userId = remoteUser.userId,
                phoneNumber = remoteUser.phoneNumber,
                nickname = remoteUser.nickname,
                avatar = remoteUser.avatar,
                signature = remoteUser.signature
            )
            
            // 保存到本地数据库
            try {
                userDao.insertUser(userEntity)
                android.util.Log.d("UserRepositoryImpl", "✅ 用户信息已保存到本地数据库 - userId: ${userId.take(8)}...")
            } catch (e: Exception) {
                android.util.Log.e("UserRepositoryImpl", "❌ 保存用户信息到本地数据库失败 - userId: ${userId.take(8)}...", e)
            }
            
            userEntity
        } catch (e: retrofit2.HttpException) {
            android.util.Log.e("UserRepositoryImpl", "❌ 从服务器获取用户信息失败 - HTTP ${e.code()}, userId: ${userId.take(8)}...", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("UserRepositoryImpl", "❌ 从服务器获取用户信息异常 - userId: ${userId.take(8)}...", e)
            null
        }
    }
    
    override suspend fun searchUser(phone: String?, userId: String?): UserEntity? {
        return try {
            // 验证输入
            if (phone.isNullOrBlank() && userId.isNullOrBlank()) {
                android.util.Log.w("UserRepository", "搜索用户：输入参数为空")
                return null
            }
            
            val trimmedPhone = phone?.trim()?.takeIf { it.isNotBlank() }
            val trimmedUserId = userId?.trim()?.takeIf { it.isNotBlank() }
            
            // 确保只有一个参数被传递
            if (trimmedPhone != null && trimmedUserId != null) {
                android.util.Log.w("UserRepository", "搜索用户：不能同时提供手机号和用户ID")
                return null
            }
            
            android.util.Log.d("UserRepository", "搜索用户 - phone: $trimmedPhone, userId: $trimmedUserId")
            
            // 调用API：Retrofit会自动跳过null的Query参数
            val remoteUser = userApi.searchUser(trimmedPhone, trimmedUserId) ?: run {
                android.util.Log.w("UserRepository", "搜索用户：API返回null")
                return null
            }
            
            // 验证响应数据
            if (remoteUser.userId.isBlank()) {
                android.util.Log.w("UserRepository", "搜索用户：返回的用户ID为空")
                return null
            }
            
            android.util.Log.d("UserRepository", "搜索用户成功 - userId: ${remoteUser.userId}, nickname: ${remoteUser.nickname}")
            
            val userEntity = UserEntity(
                userId = remoteUser.userId,
                phoneNumber = remoteUser.phoneNumber,
                nickname = remoteUser.nickname,
                avatar = remoteUser.avatar,
                signature = remoteUser.signature
            )
            
            // 保存到本地数据库（异步，不阻塞）
            try {
                userDao.insertUser(userEntity)
            } catch (e: Exception) {
                android.util.Log.w("UserRepository", "保存用户信息到数据库失败", e)
            }
            
            userEntity
        } catch (e: retrofit2.HttpException) {
            // 解析错误响应
            val errorBody = try {
                e.response()?.errorBody()?.string()
            } catch (ex: Exception) {
                null
            }
            
            android.util.Log.e("UserRepository", "搜索用户失败 - HTTP ${e.code()}: $errorBody")
            
            // 404 表示用户不存在，这是正常情况
            if (e.code() == 404) {
                null
            } else {
                // 其他HTTP错误也返回null，让上层处理
                null
            }
        } catch (e: java.io.IOException) {
            android.util.Log.e("UserRepository", "搜索用户网络异常", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "搜索用户异常", e)
            null
        }
    }
    
    override fun getUserByIdFlow(userId: String): Flow<UserEntity?> {
        return userDao.getUserByIdFlow(userId)
    }
    
    override suspend fun updateUser(user: UserEntity) {
        // 验证输入
        if (user.userId.isBlank()) {
            android.util.Log.e("UserRepository", "更新用户信息失败：用户ID为空")
            throw IllegalArgumentException("用户ID不能为空")
        }
        
        // 先更新远程
        try {
            android.util.Log.d("UserRepository", "开始更新用户信息 - userId: ${user.userId}")
            android.util.Log.d("UserRepository", "更新参数 - nickname: ${user.nickname}, avatar: ${user.avatar}, signature: ${user.signature}")
            android.util.Log.d("UserRepository", "avatar是否为null: ${user.avatar == null}, avatar是否为空: ${user.avatar.isNullOrBlank()}")
            
            // 确保头像URL不为空时才设置
            // 确保头像URL不为空时才设置（Gson默认会跳过null值）
            val request = com.tongxun.data.remote.api.UpdateUserRequest(
                nickname = user.nickname,
                avatar = user.avatar?.takeIf { it.isNotBlank() }, // 只有非空时才设置
                signature = user.signature?.takeIf { it.isNotBlank() } // 只有非空时才设置
            )
            android.util.Log.d("UserRepository", "UpdateUserRequest创建 - nickname: ${request.nickname}, avatar: ${request.avatar}, signature: ${request.signature}")
            android.util.Log.d("UserRepository", "请求序列化前检查 - avatar是否null: ${request.avatar == null}, avatar是否空白: ${request.avatar.isNullOrBlank()}")
            
            // 手动序列化检查（使用与Retrofit相同的Gson配置）
            try {
                val gson = com.google.gson.GsonBuilder()
                    .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create()
                val jsonString = gson.toJson(request)
                android.util.Log.d("UserRepository", "UpdateUserRequest JSON序列化结果: $jsonString")
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "序列化UpdateUserRequest失败", e)
            }
            
            val updatedUser = userApi.updateUser(
                user.userId,
                request
            )
            
            android.util.Log.d("UserRepository", "服务器返回更新后的用户信息 - userId: ${updatedUser.userId}, avatar: ${updatedUser.avatar}")
            
            // 验证服务器返回的数据
            if (updatedUser.userId.isBlank()) {
                android.util.Log.e("UserRepository", "服务器返回的用户ID为空")
                throw IllegalStateException("服务器返回的用户信息无效")
            }
            
            // 验证服务器返回的数据完整性
            if (updatedUser.userId.isBlank()) {
                android.util.Log.e("UserRepository", "服务器返回的用户ID为空")
                throw IllegalStateException("服务器返回的用户信息无效：用户ID为空")
            }
            
            if (updatedUser.phoneNumber.isBlank()) {
                android.util.Log.w("UserRepository", "服务器返回的手机号为空，使用原值")
            }
            
            if (updatedUser.nickname.isBlank()) {
                android.util.Log.w("UserRepository", "服务器返回的昵称为空")
            }
            
            // 更新本地数据库
            val userEntity = UserEntity(
                userId = updatedUser.userId,
                phoneNumber = updatedUser.phoneNumber.takeIf { it.isNotBlank() } ?: user.phoneNumber, // 如果服务器返回空，使用原值
                nickname = updatedUser.nickname.takeIf { it.isNotBlank() } ?: user.nickname, // 如果服务器返回空，使用原值
                avatar = updatedUser.avatar,
                signature = updatedUser.signature
            )
            
            userDao.updateUser(userEntity)
            android.util.Log.d("UserRepository", "用户信息更新成功 - userId: ${userEntity.userId}")
        } catch (e: retrofit2.HttpException) {
            val errorBody = try {
                e.response()?.errorBody()?.string()
            } catch (ex: Exception) {
                null
            }
            android.util.Log.e("UserRepository", "更新用户信息失败 - HTTP ${e.code()}: $errorBody", e)
            throw Exception("更新用户信息失败: ${errorBody ?: e.message ?: "网络错误"}")
        } catch (e: java.io.IOException) {
            android.util.Log.e("UserRepository", "更新用户信息失败：网络连接错误", e)
            throw Exception("网络连接失败，请检查网络设置")
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "更新用户信息失败", e)
            throw e
        }
    }
}

