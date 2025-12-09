package com.tongxun.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tongxun.R
import com.tongxun.data.remote.NetworkModule
import com.tongxun.data.remote.WebSocketManager
import com.tongxun.databinding.ActivityMainBinding
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.ui.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import android.widget.Toast
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    @Inject
    lateinit var webSocketManager: WebSocketManager
    
    // 获取 MainViewModel 实例，触发其初始化
    private val mainViewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("MainActivity", "🔥🔥🔥 MainActivity.onCreate() 被调用 - 代码已更新 🔥🔥🔥")
        android.util.Log.d("MainActivity", "=== MainActivity.onCreate() ===")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewPager()
        setupBottomNavigation()
        
        // 先初始化WebSocket，确保token和URL已设置
        initWebSocket()
        
        // 检查是否需要显示好友请求页面
        handleShowFriendRequestIntent()
        
        // MainViewModel 会在被引用时自动初始化
        // 强制访问 mainViewModel 以确保它被创建
        android.util.Log.e("MainActivity", "🔥🔥🔥 强制访问 MainViewModel: ${mainViewModel.hashCode()} 🔥🔥🔥")
        android.util.Log.d("MainActivity", "MainViewModel 已获取: $mainViewModel")
        
        // 监听账号被踢事件
        observeAccountKicked()
        
        // 确保WebSocket已连接（如果未连接则连接）
        ensureWebSocketConnected()
        
        android.util.Log.d("MainActivity", "MainActivity.onCreate() 完成")
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.e("MainActivity", "🔥🔥🔥 MainActivity.onResume() 被调用")
        // 在onResume时检查WebSocket连接状态
        checkAndReconnectWebSocket()
        
        // 检查是否需要显示好友请求页面
        if (intent.getBooleanExtra("show_friend_request", false)) {
            android.util.Log.d("MainActivity", "onResume中检查到跳转标志")
            binding.viewPager.postDelayed({
                if (binding.viewPager.currentItem == 1) {
                    android.util.Log.d("MainActivity", "已在联系人页面，直接发送FragmentResult")
                    supportFragmentManager.setFragmentResult("show_friend_request", Bundle())
                    intent.removeExtra("show_friend_request")
                } else {
                    handleShowFriendRequestIntent()
                }
            }, 200)
        }
    }
    
    /**
     * 检查并重新连接WebSocket
     */
    private fun checkAndReconnectWebSocket() {
        lifecycleScope.launch {
            delay(300)
            if (!webSocketManager.isConnected()) {
                android.util.Log.e("MainActivity", "❌❌❌ onResume时检测到WebSocket未连接，尝试重新连接")
                val token = authRepository.getToken()
                if (token != null) {
                    val baseUrl = NetworkModule.BASE_URL.replace("/api/", "").trimEnd('/')
                    webSocketManager.initialize(baseUrl, token)
                    android.util.Log.e("MainActivity", "✅ WebSocket已重新初始化，强制MainViewModel重新连接")
                    // 强制MainViewModel重新连接
                    mainViewModel.reconnectWebSocket()
                } else {
                    android.util.Log.e("MainActivity", "❌ Token为空，无法重新连接WebSocket")
                }
            } else {
                android.util.Log.e("MainActivity", "✅✅✅ onResume时WebSocket已连接")
            }
        }
    }
    
    /**
     * 确保WebSocket已连接
     */
    private fun ensureWebSocketConnected() {
        lifecycleScope.launch {
            // 延迟一小段时间，确保MainViewModel已初始化
            delay(200)
            
            // 检查WebSocket是否已连接
            if (!webSocketManager.isConnected()) {
                android.util.Log.e("MainActivity", "❌❌❌ WebSocket未连接，检查初始化状态")
                // 检查是否已初始化
                val token = authRepository.getToken()
                if (token != null) {
                    android.util.Log.e("MainActivity", "Token存在，但WebSocket未连接，MainViewModel应该会自动连接")
                    // 再次初始化，确保配置正确
                    val baseUrl = NetworkModule.BASE_URL.replace("/api/", "").trimEnd('/')
                    webSocketManager.initialize(baseUrl, token)
                    android.util.Log.e("MainActivity", "✅ WebSocket已重新初始化，等待MainViewModel连接")
                } else {
                    android.util.Log.e("MainActivity", "❌ Token为空，无法连接WebSocket")
                }
            } else {
                android.util.Log.e("MainActivity", "✅✅✅ WebSocket已连接")
            }
        }
    }
    
    private fun observeAccountKicked() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.shouldNavigateToLogin.collect { message ->
                    message?.let {
                        android.util.Log.e("MainActivity", "收到账号被踢通知，跳转到登录页面 - message: $it")
                        
                        // 断开WebSocket连接
                        try {
                            webSocketManager.disconnect()
                            android.util.Log.d("MainActivity", "WebSocket已断开")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "断开WebSocket失败", e)
                        }
                        
                        // 使用全局管理器处理（会显示Toast并跳转）
                        com.tongxun.utils.AccountKickedManager.handleAccountKicked(this@MainActivity, it)
                        
                        // 清除标志
                        mainViewModel.clearNavigateToLogin()
                    }
                }
            }
        }
    }
    
    private fun initWebSocket() {
        android.util.Log.d("MainActivity", "=== MainActivity.initWebSocket() 开始 ===")
        
        val token = authRepository.getToken()
        android.util.Log.d("MainActivity", "获取Token - token存在: ${token != null}, token长度: ${token?.length ?: 0}")
        
        if (token != null) {
            // 初始化WebSocket（只初始化，不连接）
            // 连接由 MainViewModel 负责，避免重复连接
            // BASE_URL是 http://47.116.197.230:3000/api/
            // Socket.IO URL应该是 http://47.116.197.230:3000（注意：Socket.IO使用http/https，不是ws/wss）
            val baseUrl = NetworkModule.BASE_URL.replace("/api/", "").trimEnd('/')
            android.util.Log.d("MainActivity", "初始化WebSocket配置 - BASE_URL: ${NetworkModule.BASE_URL}, baseUrl: $baseUrl, token: ${token.take(10)}...")
            
            webSocketManager.initialize(baseUrl, token)
            android.util.Log.d("MainActivity", "✅ WebSocket配置完成，连接由MainViewModel管理")
        } else {
            android.util.Log.w("MainActivity", "❌ Token为空，无法初始化WebSocket")
        }
        
        android.util.Log.d("MainActivity", "=== MainActivity.initWebSocket() 结束 ===")
    }
    
    private fun setupViewPager() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false // 禁用滑动切换
        
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNavigation.selectedItemId = when (position) {
                    0 -> R.id.nav_messages
                    1 -> R.id.nav_contacts
                    2 -> R.id.nav_discover
                    3 -> R.id.nav_me
                    else -> R.id.nav_messages
                }
            }
        })
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.viewPager.currentItem = when (item.itemId) {
                R.id.nav_messages -> 0
                R.id.nav_contacts -> 1
                R.id.nav_discover -> 2
                R.id.nav_me -> 3
                else -> 0
            }
            true
        }
    }
    
    private fun handleShowFriendRequestIntent() {
        if (intent.getBooleanExtra("show_friend_request", false)) {
            android.util.Log.d("MainActivity", "收到跳转好友请求的Intent")
            // 切换到联系人页面
            binding.viewPager.post {
                binding.viewPager.currentItem = 1 // 联系人页面
                binding.bottomNavigation.selectedItemId = R.id.nav_contacts
                // 延迟更长时间确保Fragment已经创建并完成onViewCreated
                binding.viewPager.postDelayed({
                    android.util.Log.d("MainActivity", "发送FragmentResult通知显示好友请求")
                    // 通知ContactFragment显示好友请求
                    supportFragmentManager.setFragmentResult("show_friend_request", Bundle())
                    // 清除Intent标志，避免重复触发
                    intent.removeExtra("show_friend_request")
                }, 500)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.d("MainActivity", "onNewIntent被调用")
        handleShowFriendRequestIntent()
    }
}
