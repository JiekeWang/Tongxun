package com.tongxun.data.di

import com.tongxun.data.remote.WebSocketManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebSocketModule {
    
    @Provides
    @Singleton
    fun provideWebSocketManager(): WebSocketManager {
        return WebSocketManager.getInstance()
    }
}

