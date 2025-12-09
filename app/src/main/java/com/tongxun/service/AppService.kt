package com.tongxun.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tongxun.R
import android.R as AndroidR
import com.tongxun.TongxunApplication
import com.tongxun.data.remote.WebSocketManager
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppService : Service() {
    
    @Inject
    lateinit var webSocketManager: WebSocketManager
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val CHANNEL_ID = "tongxun_service_channel"
    private val NOTIFICATION_ID = 1
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 初始化WebSocket连接
        serviceScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser != null) {
                // WebSocket连接已在MainActivity中处理
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // 服务已启动
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY // 服务被杀死后自动重启
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            webSocketManager.disconnect()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "通讯服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持应用后台运行以接收消息"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("通讯")
            .setContentText("正在后台运行")
            .setSmallIcon(AndroidR.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    companion object {
        const val ACTION_START = "com.tongxun.service.START"
        const val ACTION_STOP = "com.tongxun.service.STOP"
        
        fun startService(context: android.content.Context) {
            val intent = Intent(context, AppService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: android.content.Context) {
            val intent = Intent(context, AppService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
}
