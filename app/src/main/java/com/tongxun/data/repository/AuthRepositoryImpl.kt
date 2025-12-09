package com.tongxun.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tongxun.data.local.TongxunDatabase
import com.tongxun.data.local.entity.UserEntity
import com.tongxun.data.remote.api.AuthApi
import com.tongxun.data.remote.dto.LoginRequest
import com.tongxun.data.remote.dto.RegisterRequest
import com.tongxun.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val database: TongxunDatabase,
    @ApplicationContext private val context: Context
) : AuthRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val userDao = database.userDao()
    private val messageDao = database.messageDao()
    private val conversationDao = database.conversationDao()
    private val friendDao = database.friendDao()
    private val groupDao = database.groupDao()
    
    override suspend fun login(phoneNumber: String, password: String): Result<UserEntity> {
        android.util.Log.e("AuthRepository", "🔥🔥🔥 === login() 被调用 ===")
        android.util.Log.e("AuthRepository", "🔥 参数 - phoneNumber长度: ${phoneNumber.length}, password长度: ${password.length}")
        android.util.Log.e("AuthRepository", "🔥 phoneNumber内容: ${phoneNumber.take(3)}***")
        
        // 再次验证输入（防御性编程）
        if (phoneNumber.isBlank() || password.isBlank()) {
            android.util.Log.w("AuthRepository", "手机号或密码为空")
            return Result.failure(Exception("手机号和密码不能为空"))
        }
        
        if (phoneNumber.length != 11 || !phoneNumber.matches(Regex("^1[3-9]\\d{9}$"))) {
            android.util.Log.w("AuthRepository", "手机号格式不正确 - 长度: ${phoneNumber.length}, 匹配: ${phoneNumber.matches(Regex("^1[3-9]\\d{9}$"))}")
            return Result.failure(Exception("手机号格式不正确"))
        }
        
        if (password.length < 6) {
            android.util.Log.w("AuthRepository", "密码长度不足 - 长度: ${password.length}")
            return Result.failure(Exception("密码长度至少6位"))
        }
        
        android.util.Log.e("AuthRepository", "✅ 验证通过，准备发送登录请求")
        
        return try {
            val loginRequest = LoginRequest(phoneNumber, password)
            android.util.Log.e("AuthRepository", "🔥 创建LoginRequest - phoneNumber长度: ${loginRequest.phoneNumber.length}, password长度: ${loginRequest.password.length}")
            android.util.Log.e("AuthRepository", "🔥 phoneNumber内容: ${loginRequest.phoneNumber.take(3)}***")
            
            if (loginRequest.phoneNumber.isBlank()) {
                android.util.Log.e("AuthRepository", "❌❌❌ LoginRequest中的phoneNumber为空！")
                return Result.failure(Exception("手机号不能为空"))
            }
            
            android.util.Log.e("AuthRepository", "🔥 准备调用 authApi.login()")
            val response = authApi.login(loginRequest)
            android.util.Log.e("AuthRepository", "✅✅✅ 收到登录响应 - token长度: ${response.token.length}")
            android.util.Log.e("AuthRepository", "🔥 response.user: ${response.user}")
            android.util.Log.e("AuthRepository", "🔥 response.user.userId: ${response.user.userId}")
            
            // 验证响应数据完整性
            if (response.token.isBlank()) {
                android.util.Log.e("AuthRepository", "❌ 服务器返回的Token为空")
                return Result.failure(Exception("服务器返回的Token为空"))
            }
            
            val userId = response.user.userId
            if (userId.isBlank()) {
                android.util.Log.e("AuthRepository", "❌ 服务器返回的用户ID为空 - userId: $userId")
                return Result.failure(Exception("服务器返回的用户ID为空"))
            }
            
            // 检查是否切换了用户
            val previousUserId = prefs.getString("current_user_id", null)
            val isUserSwitched = previousUserId != null && previousUserId != userId
            
            if (isUserSwitched) {
                android.util.Log.w("AuthRepository", "检测到用户切换 - 前一个用户: $previousUserId, 新用户: $userId")
                android.util.Log.w("AuthRepository", "开始清理前一个用户的所有本地数据")
                // 清理前一个用户的所有本地数据
                clearAllLocalData()
            }
            
            saveToken(response.token)
            // 登录后也将 access token 作为 refresh token 使用（与注册保持一致）
            saveRefreshToken(response.token)
            
            val userEntity = UserEntity(
                userId = response.user.userId,
                phoneNumber = response.user.phoneNumber,
                nickname = response.user.nickname,
                avatar = response.user.avatar,
                signature = response.user.signature
            )
            
            // 保存用户信息到数据库
            try {
                userDao.insertUser(userEntity)
                // 保存当前用户ID（使用 commit() 确保立即写入）
                prefs.edit().putString("current_user_id", userEntity.userId).commit()
            } catch (e: Exception) {
                // 如果数据库保存失败，记录错误但不影响登录流程
                android.util.Log.e("AuthRepository", "保存用户信息到数据库失败", e)
            }
            
            // 登录成功后，清除账号被踢事件状态
            com.tongxun.utils.AccountKickedManager.markLoggedIn()
            
            Result.success(userEntity)
        } catch (e: HttpException) {
            // 解析服务器返回的错误信息
            val errorMessage = parseErrorMessage(e)
            Result.failure(Exception(errorMessage))
        } catch (e: IOException) {
            Result.failure(Exception("网络连接失败，请检查网络设置"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "登录失败，请稍后重试"))
        }
    }
    
    override suspend fun register(phoneNumber: String, password: String, nickname: String): Result<UserEntity> {
        // 再次验证输入（防御性编程）
        if (phoneNumber.isBlank() || password.isBlank() || nickname.isBlank()) {
            return Result.failure(Exception("所有字段不能为空"))
        }
        
        if (phoneNumber.length != 11 || !phoneNumber.matches(Regex("^1[3-9]\\d{9}$"))) {
            return Result.failure(Exception("手机号格式不正确"))
        }
        
        if (password.length < 6 || password.length > 50) {
            return Result.failure(Exception("密码长度必须在6-50位之间"))
        }
        
        if (nickname.length < 1 || nickname.length > 50) {
            return Result.failure(Exception("昵称长度必须在1-50个字符之间"))
        }
        
        return try {
            val response = authApi.register(RegisterRequest(phoneNumber, password, nickname))
            
            // 验证响应数据完整性
            if (response.token.isBlank()) {
                return Result.failure(Exception("服务器返回的Token为空"))
            }
            
            if (response.user.userId.isBlank()) {
                return Result.failure(Exception("服务器返回的用户ID为空"))
            }
            
            val userId = response.user.userId
            
            // 检查是否切换了用户（注册时如果之前有登录用户，也需要清理）
            val previousUserId = prefs.getString("current_user_id", null)
            val isUserSwitched = previousUserId != null && previousUserId != userId
            
            if (isUserSwitched) {
                android.util.Log.w("AuthRepository", "注册时检测到用户切换 - 前一个用户: $previousUserId, 新用户: $userId")
                android.util.Log.w("AuthRepository", "开始清理前一个用户的所有本地数据")
                // 清理前一个用户的所有本地数据
                clearAllLocalData()
            }
            
            saveToken(response.token)
            
            val userEntity = UserEntity(
                userId = response.user.userId,
                phoneNumber = response.user.phoneNumber,
                nickname = response.user.nickname,
                avatar = response.user.avatar,
                signature = response.user.signature
            )
            
            // 保存用户信息到数据库
            try {
                userDao.insertUser(userEntity)
            } catch (e: Exception) {
                // 如果数据库保存失败，记录错误但不影响注册流程
                android.util.Log.e("AuthRepository", "保存用户信息到数据库失败", e)
            }
            
            // 保存当前用户ID
            prefs.edit().putString("current_user_id", userEntity.userId).commit()
            // 注册后暂时将 access token 也作为 refresh token 使用
            saveRefreshToken(response.token)
            
            // 注册成功后，标记已登录
            com.tongxun.utils.AccountKickedManager.markLoggedIn()
            
            Result.success(userEntity)
        } catch (e: HttpException) {
            // 解析服务器返回的错误信息
            val errorMessage = parseErrorMessage(e)
            Result.failure(Exception(errorMessage))
        } catch (e: IOException) {
            Result.failure(Exception("网络连接失败，请检查网络设置"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "注册失败，请稍后重试"))
        }
    }
    
    override suspend fun refreshToken(): Result<String> {
        android.util.Log.d("AuthRepository", "=== refreshToken() 被调用 ===")
        val refreshToken = getRefreshToken()
        if (refreshToken == null) {
            android.util.Log.w("AuthRepository", "没有刷新令牌")
            return Result.failure(Exception("没有刷新令牌"))
        }
        
        android.util.Log.d("AuthRepository", "准备刷新Token - refreshToken长度: ${refreshToken.length}")
        
        return try {
            val response = authApi.refreshToken(
                com.tongxun.data.remote.api.RefreshTokenRequest(refreshToken)
            )
            android.util.Log.d("AuthRepository", "Token刷新成功 - 新token长度: ${response.token.length}")
            saveToken(response.token)
            Result.success(response.token)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Token刷新失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun checkAutoLogin(): Result<UserEntity> {
        android.util.Log.d("AuthRepository", "=== checkAutoLogin() 被调用 ===")
        val token = getToken()
        if (token == null) {
            android.util.Log.d("AuthRepository", "没有Token，无法自动登录")
            return Result.failure(Exception("未登录"))
        }
        
        android.util.Log.d("AuthRepository", "找到Token，检查本地用户信息")
        
        // 先检查本地用户信息是否存在
        val currentUser = getCurrentUser()
        if (currentUser == null) {
            android.util.Log.w("AuthRepository", "本地没有用户信息，清除Token")
            // 如果本地没有用户信息，清除Token
            prefs.edit().remove("auth_token").remove("refresh_token").commit()
            return Result.failure(Exception("用户信息不存在"))
        }
        
        android.util.Log.d("AuthRepository", "找到本地用户信息 - userId: ${currentUser.userId}, phoneNumber: ${currentUser.phoneNumber}")
        
        // 尝试刷新Token以验证有效性（不阻塞，快速失败）
        return try {
            android.util.Log.d("AuthRepository", "开始刷新Token以验证有效性")
            val refreshResult = refreshToken()
            refreshResult.map { 
                // 刷新成功，返回当前用户信息
                android.util.Log.d("AuthRepository", "自动登录成功 - userId: ${currentUser.userId}")
                currentUser
            }
        } catch (e: Exception) {
            // 刷新失败，清除无效Token
            android.util.Log.w("AuthRepository", "自动登录Token验证失败", e)
            prefs.edit().remove("auth_token").remove("refresh_token").commit()
            Result.failure(Exception("Token已过期，请重新登录"))
        }
    }
    
    override suspend fun logout() {
        try {
            // 先调用服务器端登出API（如果Token存在）
            val token = getToken()
            if (token != null) {
                try {
                    authApi.logout()
                } catch (e: Exception) {
                    // 即使服务器端登出失败，也继续清理本地数据
                    android.util.Log.w("AuthRepository", "服务器端登出失败，继续清理本地数据", e)
                }
            }
            
            // 清理所有本地数据（消息、好友、会话等）
            android.util.Log.d("AuthRepository", "开始清理所有本地数据")
            clearAllLocalData()
            
            // 清除所有认证信息（使用 commit() 确保立即写入）
            prefs.edit().clear().commit()
            
            // 标记已登出
            com.tongxun.utils.AccountKickedManager.markLoggedOut()
            
            android.util.Log.d("AuthRepository", "登出完成，所有数据已清理")
        } catch (e: Exception) {
            // 确保即使出错也清理本地数据
            android.util.Log.e("AuthRepository", "登出过程发生错误", e)
            try {
                clearAllLocalData()
            } catch (clearError: Exception) {
                android.util.Log.e("AuthRepository", "清理数据时发生错误", clearError)
            }
            prefs.edit().clear().commit()
        }
    }
    
    /**
     * 清理所有本地数据（消息、好友、会话、群组等）
     * 用于用户切换或登出时
     */
    private suspend fun clearAllLocalData() {
        try {
            android.util.Log.d("AuthRepository", "开始清理所有本地数据表")
            
            // 清理所有消息
            messageDao.deleteAllMessages()
            android.util.Log.d("AuthRepository", "✅ 已清理所有消息")
            
            // 清理所有会话
            conversationDao.deleteAllConversations()
            android.util.Log.d("AuthRepository", "✅ 已清理所有会话")
            
            // 清理所有好友关系
            friendDao.deleteAllFriends()
            android.util.Log.d("AuthRepository", "✅ 已清理所有好友关系")
            
            // 清理所有群组
            groupDao.deleteAllGroups()
            android.util.Log.d("AuthRepository", "✅ 已清理所有群组")
            
            // 🔥 关键修复：清除已删除消息的记录（因为所有消息都被清除了，已删除记录也应该清除）
            try {
                val deletedMessagesPrefs = context.getSharedPreferences("deleted_messages", Context.MODE_PRIVATE)
                deletedMessagesPrefs.edit().clear().commit()
                android.util.Log.d("AuthRepository", "✅ 已清除已删除消息记录")
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "清除已删除消息记录失败", e)
            }
            
            // 注意：不清理所有用户信息，因为可能还有其他用户的信息缓存
            // 只清理消息、会话、好友、群组等敏感数据
            
            android.util.Log.d("AuthRepository", "✅✅✅ 所有本地数据清理完成")
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "❌ 清理本地数据失败", e)
            // 即使清理失败也继续，不影响登录流程
        }
    }
    
    override fun getCurrentUser(): UserEntity? {
        val userId = prefs.getString("current_user_id", null) ?: return null
        // 注意：这是一个同步方法，但数据库操作是异步的
        // 为了性能考虑，这里使用 runBlocking，但应该尽量避免在主线程调用
        return try {
            kotlinx.coroutines.runBlocking {
                userDao.getUserById(userId)
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "获取用户信息失败", e)
            null
        }
    }
    
    override fun isLoggedIn(): Boolean {
        return getToken() != null && getCurrentUser() != null
    }
    
    override fun saveToken(token: String) {
        // 使用 commit() 确保数据立即写入，避免丢失
        // 虽然 apply() 性能更好，但对于关键数据（Token）应该使用 commit()
        prefs.edit().putString("auth_token", token).commit()
    }
    
    override fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }
    
    override fun saveRefreshToken(token: String) {
        // 使用 commit() 确保数据立即写入
        prefs.edit().putString("refresh_token", token).commit()
    }
    
    override fun getRefreshToken(): String? {
        return prefs.getString("refresh_token", null)
    }
    
    /**
     * 解析服务器返回的错误信息
     * 支持格式：
     * - { "error": "错误信息" }
     * - { "errors": [{ "msg": "错误信息", "param": "字段名" }] }
     */
    private fun parseErrorMessage(e: HttpException): String {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val gson = Gson()
                val jsonObject = gson.fromJson(errorBody, JsonObject::class.java)
                
                // 尝试获取 error 字段（单个错误）
                if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive) {
                    return jsonObject.get("error").asString
                }
                // 尝试获取 errors 数组（验证错误）
                else if (jsonObject.has("errors")) {
                    val errorsArray = jsonObject.getAsJsonArray("errors")
                    if (errorsArray.size() > 0) {
                        val firstError = errorsArray[0].asJsonObject
                        // express-validator 返回格式: { "msg": "错误信息", "param": "字段名", "location": "body" }
                        val errorMsg = when {
                            firstError.has("msg") -> firstError.get("msg").asString
                            firstError.has("message") -> firstError.get("message").asString
                            else -> "请求参数错误"
                        }
                        // 如果有字段名，添加到错误信息中
                        val param = if (firstError.has("param")) {
                            firstError.get("param").asString
                        } else null
                        
                        if (param != null && param != "body") {
                            "$param: $errorMsg"
                        } else {
                            errorMsg
                        }
                    } else {
                        "请求参数错误"
                    }
                } else {
                    "请求失败 (${e.code()})"
                }
            } else {
                "请求失败 (${e.code()})"
            }
        } catch (ex: Exception) {
            "请求失败 (${e.code()})"
        }
    }
}

