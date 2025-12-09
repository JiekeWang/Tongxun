package com.tongxun.data.remote

import com.tongxun.data.remote.api.AuthApi
import com.tongxun.data.remote.api.ConversationApi
import com.tongxun.data.remote.api.FriendApi
import com.tongxun.data.remote.api.UserApi
import com.tongxun.data.remote.interceptor.AuthInterceptor
import com.tongxun.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    // 生产环境配置：
    // 公网域名：zhihome.com.cn（需要ICP备案）
    // 公网IP：47.116.197.230
    // 当前使用IP地址（域名需要备案，备案期间使用IP）
    const val BASE_URL = "http://47.116.197.230:3000/api/"
    // 如果域名已完成ICP备案，可以使用域名：
    // const val BASE_URL = "http://zhihome.com.cn:3000/api/"
    // 如果配置了Nginx反向代理和HTTPS，使用：
    // const val BASE_URL = "https://zhihome.com.cn/api/"
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideFriendApi(retrofit: Retrofit): FriendApi {
        return retrofit.create(FriendApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideMessageApi(retrofit: Retrofit): com.tongxun.data.remote.api.MessageApi {
        return retrofit.create(com.tongxun.data.remote.api.MessageApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideUploadApi(retrofit: Retrofit): com.tongxun.data.remote.api.UploadApi {
        return retrofit.create(com.tongxun.data.remote.api.UploadApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideGroupApi(retrofit: Retrofit): com.tongxun.data.remote.api.GroupApi {
        return retrofit.create(com.tongxun.data.remote.api.GroupApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideConversationApi(retrofit: Retrofit): ConversationApi {
        return retrofit.create(ConversationApi::class.java)
    }
}

