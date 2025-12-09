package com.tongxun.utils

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tongxun.ui.auth.LoginActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 全局账号被踢事件管理器
 * 用于在所有Activity中监听账号被踢事件并强制跳转到登录页面
 */
object AccountKickedManager {
    private const val TAG = "AccountKickedManager"
    
    private val _accountKickedEvent = MutableSharedFlow<String>(replay = 0) // 改为 replay = 0，不保留历史事件
    val accountKickedEvent: SharedFlow<String> = _accountKickedEvent.asSharedFlow()
    
    // 标记是否正在处理跳转，防止重复跳转
    @Volatile
    private var isNavigatingToLogin = false
    
    // 记录已经处理过的Activity，避免同一个Activity重复处理
    private val processedActivities = mutableSetOf<String>()
    
    // 存储当前活跃的Activity列表（用于立即处理事件）
    private val activeActivities = mutableSetOf<Activity>()
    
    // 标记是否已登录（登录成功后设为 true，可以忽略旧的账号被踢事件）
    @Volatile
    private var isLoggedIn = false
    
    /**
     * 发送账号被踢事件
     */
    fun notifyAccountKicked(message: String) {
        Log.e(TAG, "🔥🔥🔥 发送账号被踢事件 - message: $message, isLoggedIn: $isLoggedIn, 活跃Activity数: ${activeActivities.size}")
        // 账号被踢时，标记为已登出（这样后续处理会清除数据）
        isLoggedIn = false
        isNavigatingToLogin = false // 重置标志，允许新的跳转
        processedActivities.clear() // 清空已处理的Activity记录
        
        // 立即处理所有活跃的Activity（不等待Flow收集器）
        synchronized(activeActivities) {
            val activitiesToHandle = activeActivities.filter { 
                it !is LoginActivity && !it.isFinishing && !it.isDestroyed 
            }
            Log.e(TAG, "立即处理 ${activitiesToHandle.size} 个活跃Activity")
            activitiesToHandle.forEach { activity ->
                handleAccountKicked(activity, message)
            }
        }
        
        // 同时通过Flow发送事件（供已设置的监听器使用）
        _accountKickedEvent.tryEmit(message)
    }
    
    /**
     * 标记已登录（登录成功后调用）
     */
    fun markLoggedIn() {
        Log.d(TAG, "标记已登录，清除旧的账号被踢事件状态")
        isLoggedIn = true
        isNavigatingToLogin = false
        processedActivities.clear()
    }
    
    /**
     * 标记已登出（登出时调用）
     */
    fun markLoggedOut() {
        Log.d(TAG, "标记已登出")
        isLoggedIn = false
        isNavigatingToLogin = false
        processedActivities.clear()
    }
    
    /**
     * 在Activity中设置监听，自动处理账号被踢事件
     * 使用协程在Activity的生命周期范围内实时监听事件
     */
    fun setupObserver(activity: Activity, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            private var job: kotlinx.coroutines.Job? = null
            
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        // 注册Activity到活跃列表
                        synchronized(activeActivities) {
                            activeActivities.add(activity)
                            Log.d(TAG, "注册Activity到活跃列表 - Activity: ${activity.javaClass.simpleName}, 总数: ${activeActivities.size}")
                        }
                        
                        // 在CREATE状态时立即开始监听（确保能收到事件）
                        // 使用lifecycleScope.launch确保协程在Activity销毁时自动取消
                        job = source.lifecycleScope.launch {
                            accountKickedEvent.collect { message ->
                                Log.e(TAG, "Activity收到账号被踢事件（通过Flow）- Activity: ${activity.javaClass.simpleName}, message: $message")
                                // 在主线程处理跳转
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    handleAccountKicked(activity, message)
                                }
                            }
                        }
                        Log.d(TAG, "已在Activity创建时设置监听 - Activity: ${activity.javaClass.simpleName}")
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        // 从活跃列表中移除
                        synchronized(activeActivities) {
                            activeActivities.remove(activity)
                            Log.d(TAG, "从活跃列表移除Activity - Activity: ${activity.javaClass.simpleName}, 剩余: ${activeActivities.size}")
                        }
                        // 取消监听
                        job?.cancel()
                        job = null
                    }
                    else -> {}
                }
            }
        })
    }
    
    /**
     * 处理账号被踢事件
     */
    fun handleAccountKicked(activity: Activity, message: String) {
        Log.e(TAG, "🔥🔥🔥 处理账号被踢事件 - Activity: ${activity.javaClass.simpleName}, message: $message")
        
        // 如果已经在登录页面，不需要处理
        if (activity is LoginActivity) {
            Log.d(TAG, "当前已在登录页面，跳过处理")
            return
        }
        
        // 检查Activity是否正在销毁或已销毁
        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Activity正在销毁或已销毁，跳过处理 - Activity: ${activity.javaClass.simpleName}")
            return
        }
        
        // 检查这个Activity是否已经处理过
        val activityKey = "${activity.javaClass.name}_${activity.hashCode()}"
        if (processedActivities.contains(activityKey)) {
            Log.d(TAG, "Activity已处理过，跳过 - Activity: ${activity.javaClass.simpleName}")
            return
        }
        
        // 标记为已处理（在跳转之前标记，避免并发问题）
        processedActivities.add(activityKey)
        
        // 标记为已登出
        isLoggedIn = false
        
        Log.e(TAG, "开始处理账号被踢跳转 - Activity: ${activity.javaClass.simpleName}")
        
        // 在主线程执行UI操作
        activity.runOnUiThread {
            try {
                // 显示Toast提示
                try {
                    android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "显示Toast失败", e)
                }
                
                // 再次检查Activity状态（可能在Toast显示时已销毁）
                if (activity.isFinishing || activity.isDestroyed) {
                    Log.w(TAG, "Activity在跳转前已销毁，取消跳转")
                    processedActivities.remove(activityKey)
                    return@runOnUiThread
                }
                
                // 强制跳转到登录页面
                val intent = Intent(activity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                           Intent.FLAG_ACTIVITY_CLEAR_TASK or
                           Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                activity.startActivity(intent)
                activity.finish()
                Log.e(TAG, "✅✅✅ 已强制跳转到登录页面 - Activity: ${activity.javaClass.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "跳转到登录页面失败", e)
                e.printStackTrace()
                processedActivities.remove(activityKey) // 跳转失败，移除标记
            }
        }
    }
}

