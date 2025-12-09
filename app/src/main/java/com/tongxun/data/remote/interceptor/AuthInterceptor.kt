package com.tongxun.data.remote.interceptor

import com.tongxun.domain.repository.AuthRepository
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val authRepository: Lazy<AuthRepository>
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()
        
        // 登录和注册接口不需要Token，直接放行
        val isAuthEndpoint = url.contains("/auth/login") || url.contains("/auth/register") || url.contains("/auth/refresh")
        
        val requestBuilder = originalRequest.newBuilder()
        var token: String? = null
        
        // 只有非认证接口才添加Token
        if (!isAuthEndpoint) {
            token = authRepository.get().getToken()
            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
        }
        
        var response = chain.proceed(requestBuilder.build())
        
        // 如果Token过期（401）且不是认证接口，尝试刷新Token
        if (response.code == 401 && !isAuthEndpoint) {
            synchronized(this) {
                // 防止多个请求同时刷新Token
                val currentToken = authRepository.get().getToken()
                
                // 检查Token是否已被其他请求刷新
                if (currentToken != null && currentToken != token) {
                    // Token已刷新，重新发送请求
                    response.close()
                    val newRequest = originalRequest.newBuilder()
                        .addHeader("Authorization", "Bearer $currentToken")
                        .build()
                    response = chain.proceed(newRequest)
                } else {
                    // 尝试刷新Token（最多重试一次）
                    try {
                        val refreshResult = runBlocking {
                            authRepository.get().refreshToken()
                        }
                        
                        refreshResult.onSuccess { newToken ->
                            // Token刷新成功，重新发送请求
                            response.close()
                            val newRequest = originalRequest.newBuilder()
                                .addHeader("Authorization", "Bearer $newToken")
                                .build()
                            response = chain.proceed(newRequest)
                        }.onFailure {
                            // Token刷新失败，返回原响应（会触发登录）
                            response.close()
                        }
                    } catch (e: Exception) {
                        // 刷新Token时发生异常，返回原响应
                        android.util.Log.e("AuthInterceptor", "刷新Token失败", e)
                    }
                }
            }
        }
        
        return response
    }
}

