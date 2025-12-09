package com.tongxun

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.tongxun.utils.AccountKickedManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TongxunApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 注册Activity生命周期回调，在所有Activity中自动设置账号被踢事件监听
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Activity创建时设置账号被踢事件监听（仅对非登录Activity）
                if (activity !is com.tongxun.ui.auth.LoginActivity) {
                    try {
                        // 使用Activity的生命周期来自动设置监听
                        AccountKickedManager.setupObserver(activity, activity as androidx.lifecycle.LifecycleOwner)
                        Log.d("TongxunApplication", "已为Activity设置账号被踢监听: ${activity.javaClass.simpleName}")
                    } catch (e: Exception) {
                        // 如果Activity不是LifecycleOwner，忽略错误（FragmentActivity会实现）
                        Log.w("TongxunApplication", "无法为Activity设置账号被踢监听: ${activity.javaClass.simpleName}", e)
                    }
                }
            }
            
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

