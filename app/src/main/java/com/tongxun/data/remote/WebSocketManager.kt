package com.tongxun.data.remote

import android.util.Log
import com.tongxun.data.model.MessageType
import com.tongxun.data.remote.dto.MessageDto
import com.tongxun.utils.AccountKickedManager
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import java.net.URISyntaxException

class WebSocketManager private constructor() {
    
    @Volatile
    private var socket: Socket? = null
    private var wsUrl: String? = null
    private var token: String? = null
    
    companion object {
        private const val TAG = "WebSocketManager"
        
        @Volatile
        private var INSTANCE: WebSocketManager? = null
        
        fun getInstance(): WebSocketManager {
            return INSTANCE ?: synchronized(this) {
                val instance = WebSocketManager()
                INSTANCE = instance
                instance
            }
        }
    }
    
    fun initialize(baseUrl: String, token: String) {
        Log.e(TAG, "🔥🔥🔥 WebSocketManager.initialize() 被调用 ===")
        Log.e(TAG, "参数 - baseUrl: $baseUrl, token长度: ${token.length}")
        
        synchronized(this) {
            // 🔥 关键修复：如果token变化了，先断开旧连接
            val oldToken = this.token
            val tokenChanged = oldToken != null && oldToken != token
            
            // Socket.IO URL格式：http://host:port（不包含路径）
            // 路径通过 IO.Options.path 设置
            // 注意：Socket.IO客户端使用 http/https，不是 ws/wss
            // 移除末尾的 / 如果有
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val urlChanged = this.wsUrl != null && this.wsUrl != cleanBaseUrl
            
            if (tokenChanged || urlChanged) {
                Log.e(TAG, "⚠️⚠️⚠️ Token或URL发生变化，先断开旧连接 - tokenChanged: $tokenChanged, urlChanged: $urlChanged")
                if (socket != null) {
                    try {
                        socket?.off() // 移除所有事件监听器
                        socket?.disconnect() // 断开连接
                        Log.e(TAG, "✅ 旧WebSocket连接已断开")
                    } catch (e: Exception) {
                        Log.e(TAG, "断开旧连接时出错", e)
                    }
                }
                socket = null
            }
            
            this.token = token
            this.wsUrl = cleanBaseUrl
        
            Log.e(TAG, "✅✅✅ WebSocket初始化完成 - baseUrl: $baseUrl, wsUrl: $wsUrl, token: ${token.take(20)}...")
            Log.e(TAG, "当前socket状态 - socket存在: ${socket != null}, connected: ${socket?.connected()}")
        }
    }
    
    fun connect(): Flow<ConnectionState> = callbackFlow {
        Log.e(TAG, "🔥🔥🔥 WebSocketManager.connect() 被调用 - wsUrl: $wsUrl, token存在: ${token != null}")
        
        val url = wsUrl ?: run {
            Log.e(TAG, "❌❌❌ WebSocket URL未初始化 - wsUrl is null, token存在: ${token != null}")
            trySend(ConnectionState.Disconnected("WebSocket URL not initialized"))
            close()
            return@callbackFlow
        }
        
        val currentToken = token ?: run {
            Log.e(TAG, "❌❌❌ WebSocket Token未初始化 - token is null")
            trySend(ConnectionState.Disconnected("WebSocket Token not initialized"))
            close()
            return@callbackFlow
        }
        
        Log.e(TAG, "✅✅✅ WebSocket URL和Token已初始化 - URL: $url, token: ${currentToken.take(20)}...")
        
        // 如果已有连接且已连接，也需要注册事件监听器，确保新Flow能收到消息
        // 🔥 注意：Socket.IO支持多个监听器，所以可以直接添加新的监听器
        // 但是为了避免重复处理，我们在复用连接时也会注册新的监听器
        if (socket != null && socket!!.connected()) {
            val socketId = socket!!.id()
            Log.e(TAG, "✅ 检测到已有WebSocket连接且已连接，复用现有连接并注册新监听器 - socketId: $socketId")
            // 注册事件监听器，确保新Flow能收到消息
            // Socket.IO支持多个监听器，每个Flow都有自己的监听器，这是正确的行为
            setupSocketListeners(socket!!, this@callbackFlow)
            // 直接发送连接状态
            trySend(ConnectionState.Connected)
            Log.e(TAG, "✅✅✅ 已复用现有连接并注册监听器 - socketId: $socketId")
            // 等待Flow关闭时不断开连接（因为其他Flow可能还在使用）
            awaitClose {
                Log.d(TAG, "Flow关闭，但不断开WebSocket连接（可能其他Flow仍在使用）")
            }
            return@callbackFlow
        }
        
        // 如果socket存在但未连接，先断开
        if (socket != null && !socket!!.connected()) {
            Log.d(TAG, "检测到已有WebSocket连接但未连接，先断开旧连接")
            disconnect()
        }
        
        Log.d(TAG, "🚀 开始连接WebSocket - URL: $url")
        
        try {
            val options = IO.Options().apply {
                // Socket.IO 路径配置（服务器端配置为 /ws）
                path = "/ws"
                // 只使用 WebSocket 传输，不使用 polling
                transports = arrayOf("websocket")
                // 认证通过 query 参数传递
                query = "token=$token"
                // 超时设置（毫秒）
                timeout = 20000
                // 重连配置
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
            }
            
            // Socket.IO 客户端需要完整的 URL（包含协议，但不包含路径）
            val socketUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "http://$url"
            }
            
            Log.d(TAG, "Socket.IO连接配置 - URL: $socketUrl, path: ${options.path}, query: ${options.query}")
            
            // 只有在socket不存在时才创建新的
            val isNewSocket = socket == null
            val currentSocket = if (isNewSocket) {
                Log.e(TAG, "🚀 准备创建新的WebSocket实例...")
                val newSocket = IO.socket(socketUrl, options)
                synchronized(this@WebSocketManager) {
                    this@WebSocketManager.socket = newSocket
                    Log.e(TAG, "✅✅✅ 新的WebSocket实例已创建并保存 - socketId: ${newSocket.id()}, socket实例存在: ${this@WebSocketManager.socket != null}")
                }
                Log.e(TAG, "✅✅✅ 创建新的WebSocket实例完成 - socketId: ${newSocket.id()}")
                newSocket
            } else {
                Log.e(TAG, "✅ 复用现有WebSocket实例 - socketId: ${socket?.id()}, socket实例存在: ${socket != null}")
                socket!!
            }
            
            // 注册事件监听器（Socket.IO支持多个监听器，所以可以重复注册）
            // 每个Flow都会收到事件，这是正确的行为
            setupSocketListeners(currentSocket, this@callbackFlow)
            
            // 如果socket未连接，则连接
            if (!currentSocket.connected()) {
                Log.e(TAG, "🚀 准备发起WebSocket连接请求 - socketId: ${currentSocket.id()}")
                currentSocket.connect()
                Log.e(TAG, "✅✅✅ 已发起WebSocket连接请求 - socketId: ${currentSocket.id()}, connected: ${currentSocket.connected()}")
            } else {
                Log.e(TAG, "✅ WebSocket已连接，无需重新连接 - socketId: ${currentSocket.id()}")
                // 如果已连接，立即发送连接状态
                trySend(ConnectionState.Connected)
            }
            
        } catch (e: URISyntaxException) {
            Log.e(TAG, "WebSocket URL格式错误: $url", e)
            trySend(ConnectionState.Disconnected("Invalid URL: ${e.message}"))
            close()
        } catch (e: Exception) {
            Log.e(TAG, "创建WebSocket连接失败", e)
            trySend(ConnectionState.Disconnected("Connection failed: ${e.message}"))
            close()
        }
        
        awaitClose {
            // 注意：不断开socket连接，因为可能有其他Flow在使用
            // 只有当所有Flow都关闭时，才应该断开连接
            // 这里只记录日志，实际断开由disconnect()方法处理
            Log.d(TAG, "Flow关闭，但保持WebSocket连接（可能其他Flow仍在使用）")
        }
    }
    
    /**
     * 设置socket事件监听器（每个Flow都会注册自己的监听器）
     */
    private fun setupSocketListeners(socket: Socket, callbackFlow: kotlinx.coroutines.channels.ProducerScope<ConnectionState>) {
        // 连接事件
        socket.on(Socket.EVENT_CONNECT) {
            Log.e(TAG, "✅✅✅ WebSocket连接成功 - socketId: ${socket.id()}, socket实例存在: ${this@WebSocketManager.socket != null}")
            // 确保socket实例已保存
            synchronized(this@WebSocketManager) {
                if (this@WebSocketManager.socket == null) {
                    Log.e(TAG, "⚠️⚠️⚠️ socket实例丢失，重新设置 - socketId: ${socket.id()}")
                    this@WebSocketManager.socket = socket
                }
            }
            callbackFlow.trySend(ConnectionState.Connected)
        }
            
        socket.on(Socket.EVENT_DISCONNECT) { args ->
                val reason = if (args.isNotEmpty()) args[0].toString() else "Unknown"
                Log.w(TAG, "WebSocket断开: $reason")
            callbackFlow.trySend(ConnectionState.Disconnected(reason))
            }
            
        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = if (args.isNotEmpty()) args[0].toString() else "Unknown error"
                Log.e(TAG, "WebSocket连接错误: $error")
            callbackFlow.trySend(ConnectionState.Disconnected(error))
            }
            
            // 重连事件（使用字符串字面量，因为某些版本可能没有这些常量）
        socket.on("reconnect") { args ->
                val attemptCount = if (args.isNotEmpty()) args[0].toString() else "?"
                Log.d(TAG, "WebSocket重连成功 - 尝试次数: $attemptCount")
            callbackFlow.trySend(ConnectionState.Connected)
            }
            
        socket.on("reconnect_attempt") { args ->
                val attemptCount = if (args.isNotEmpty()) args[0].toString() else "?"
                Log.d(TAG, "WebSocket正在重连 - 尝试次数: $attemptCount")
            }
            
        socket.on("reconnect_error") { args ->
                val error = if (args.isNotEmpty()) args[0].toString() else "Unknown error"
                Log.e(TAG, "WebSocket重连错误: $error")
            }
            
        socket.on("reconnect_failed") {
                Log.e(TAG, "WebSocket重连失败 - 已达到最大重连次数")
            callbackFlow.trySend(ConnectionState.Disconnected("Reconnection failed"))
            }
            
            // 错误事件
        socket.on("error") { args ->
                val error = if (args.isNotEmpty()) args[0].toString() else "Unknown error"
                Log.e(TAG, "WebSocket错误: $error")
            }
            
            // 服务器发送的连接成功事件
        socket.on("connected") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as? JSONObject
                        Log.d(TAG, "收到服务器连接确认: $data")
                    } catch (e: Exception) {
                        Log.w(TAG, "解析连接确认失败", e)
                    }
                }
            }
            
            // 接收消息事件
        socket.on("message") { args ->
            Log.e(TAG, "🔥🔥🔥 收到message事件 - args数量: ${args.size}, socketId: ${socket.id()}")
                if (args.isNotEmpty()) {
                    try {
                    Log.e(TAG, "🔥 message事件参数类型: ${args[0]?.javaClass?.simpleName}")
                        val data = args[0] as? JSONObject
                        if (data != null) {
                        Log.e(TAG, "✅ 收到message事件 - data: $data")
                            val messageDto = parseMessage(data)
                            if (messageDto != null) {
                            Log.e(TAG, "✅ 解析消息成功 - messageId=${messageDto.messageId}, conversationId=${messageDto.conversationId}, senderId=${messageDto.senderId}, receiverId=${messageDto.receiverId}, content=${messageDto.content.take(50)}")
                            val sent = callbackFlow.trySend(ConnectionState.MessageReceived(messageDto))
                            if (sent.isSuccess) {
                                Log.e(TAG, "✅✅✅ 已成功发送MessageReceived状态到Flow - messageId=${messageDto.messageId}")
                            } else {
                                Log.e(TAG, "❌❌❌ 发送MessageReceived状态到Flow失败 - messageId=${messageDto.messageId}, result=${sent}")
                            }
                        } else {
                            Log.e(TAG, "❌ 解析消息失败 - data: $data")
                        }
                    } else {
                        Log.e(TAG, "❌ message事件参数不是JSONObject - args[0]: ${args[0]}, 类型: ${args[0]?.javaClass?.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 处理message事件失败", e)
                    e.printStackTrace()
                    }
                } else {
                    Log.w(TAG, "⚠️ message事件参数为空")
                }
            }
            
            // 消息发送确认
        socket.on("message_sent") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as? JSONObject
                        Log.d(TAG, "收到消息发送确认: $data")
                        // 可以在这里更新消息状态
                    } catch (e: Exception) {
                        Log.w(TAG, "解析消息发送确认失败", e)
                    }
                }
            }
            
            // 消息撤回通知
        socket.on("message_recalled") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as? JSONObject
                        val messageId = data?.optString("messageId", "")
                        if (messageId != null && messageId.isNotBlank()) {
                            Log.d(TAG, "收到撤回消息通知 - messageId=$messageId")
                        callbackFlow.trySend(ConnectionState.MessageRecalled(messageId))
                                    }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理撤回消息通知失败", e)
                    }
                }
            }
            
            // 好友请求通知
        socket.on("friend_request") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as? JSONObject
                        val requestId = data?.optString("requestId", "")
                        val fromUserId = data?.optString("fromUserId", "")
                        if (requestId != null && requestId.isNotBlank() && fromUserId != null && fromUserId.isNotBlank()) {
                            Log.d(TAG, "收到好友请求通知 - requestId=$requestId, fromUserId=$fromUserId")
                        callbackFlow.trySend(ConnectionState.FriendRequestReceived(
                                requestId = requestId,
                                fromUserId = fromUserId,
                                fromUserNickname = data.optString("fromUserNickname", ""),
                            fromUserAvatar = data.optString("fromUserAvatar", "").takeIf { it.isNotBlank() },
                            message = data.optString("message", "").takeIf { it.isNotBlank() },
                                timestamp = data.optLong("timestamp", System.currentTimeMillis())
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理好友请求通知失败", e)
                    }
                }
            }
            
        // 账号被踢通知（单设备登录）
        socket.on("account_kicked") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    val reason = data?.optString("reason", "账号在其他设备登录") ?: "账号在其他设备登录"
                    val message = data?.optString("message", "您的账号在其他设备登录，当前设备已下线") ?: "您的账号在其他设备登录，当前设备已下线"
                    Log.e(TAG, "🔥🔥🔥 收到账号被踢通知 - reason=$reason, message=$message")
                    
                    // 立即通知全局管理器（不依赖Flow收集器）
                    try {
                        AccountKickedManager.notifyAccountKicked(message)
                        Log.e(TAG, "✅ 已立即通知AccountKickedManager")
                    } catch (e: Exception) {
                        Log.e(TAG, "通知AccountKickedManager失败", e)
                    }
                    
                    // 同时通过Flow发送状态（供MainViewModel处理数据清除）
                    callbackFlow.trySend(ConnectionState.AccountKicked(
                        reason = reason,
                        message = message
                    ))
                    Log.e(TAG, "✅ 已发送AccountKicked状态到Flow")
                    } catch (e: Exception) {
                    Log.e(TAG, "处理账号被踢通知失败", e)
                }
        }
        
        // 语音通话请求
        socket.on("voice_call_offer") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    val fromUserId = data?.optString("fromUserId", "") ?: ""
                    val toUserId = data?.optString("toUserId", "") ?: ""
                    val timestamp = data?.optLong("timestamp", System.currentTimeMillis()) ?: System.currentTimeMillis()
                    Log.d(TAG, "收到语音通话请求 - fromUserId: $fromUserId, toUserId: $toUserId")
                    // 通过Flow发送状态，供MainViewModel或其他组件处理
                    callbackFlow.trySend(ConnectionState.VoiceCallOffer(
                        fromUserId = fromUserId,
                        toUserId = toUserId,
                        timestamp = timestamp
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "处理语音通话请求失败", e)
                }
            }
        }
        }
    }
    
    /**
     * 发送自定义 WebSocket 事件
     */
    fun emitEvent(eventName: String, data: JSONObject): Boolean {
        return try {
            val currentSocket = synchronized(this) {
                this.socket
            }
            
            if (currentSocket == null || !currentSocket.connected()) {
                Log.e(TAG, "WebSocket未连接，无法发送事件: $eventName")
                return false
            }
            
            currentSocket.emit(eventName, data)
            Log.d(TAG, "已发送WebSocket事件: $eventName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "发送WebSocket事件失败: $eventName", e)
            false
        }
    }
    
    fun sendMessage(messageDto: MessageDto): Boolean {
        Log.e(TAG, "🔥🔥🔥 WebSocketManager.sendMessage() 被调用")
        Log.e(TAG, "参数 - messageId: ${messageDto.messageId}, receiverId: ${messageDto.receiverId}, content: ${messageDto.content.take(50)}")
        
        return try {
            // 使用同步方式获取socket，避免并发问题
            val currentSocket = synchronized(this) {
                this.socket
            }
            
            Log.d(TAG, "检查WebSocket连接状态 - socket存在: ${currentSocket != null}, connected: ${currentSocket?.connected()}")
            
            if (currentSocket == null) {
                Log.e(TAG, "❌❌❌ WebSocket未连接，无法发送消息 - socket为null")
                // 如果socket为null，说明可能还没有连接或者连接已断开
                // 这里不能直接连接，因为connect()是Flow，需要由MainViewModel管理
                // 但可以发送一个需要重新连接的信号
                if (wsUrl != null && token != null) {
                    Log.e(TAG, "⚠️ WebSocket配置存在，但socket为null，可能需要重新连接")
                    // 发送一个事件，通知需要重新连接（但这个需要MainViewModel监听）
                    // 暂时只记录日志，返回false让调用者稍后重试
                } else {
                    Log.e(TAG, "⚠️ WebSocket未初始化 - wsUrl或token为null")
                }
                return false
            }
            
            if (!currentSocket.connected()) {
                Log.e(TAG, "❌❌❌ WebSocket未连接，无法发送消息 - socket存在但未连接")
                // socket存在但未连接，可能是正在连接中或者连接已断开
                // 返回false，让调用者稍后重试
                return false
            }
            
            Log.d(TAG, "✅ WebSocket已连接，准备发送消息")
            
            val json = JSONObject().apply {
                put("messageId", messageDto.messageId)
                put("conversationId", messageDto.conversationId)
                put("senderId", messageDto.senderId)
                put("receiverId", messageDto.receiverId)
                put("content", messageDto.content)
                put("messageType", messageDto.messageType.name)
                put("timestamp", messageDto.timestamp)
                messageDto.extra?.let { put("extra", it) }
            }
            
            Log.e(TAG, "📤 准备发送消息JSON: $json")
            currentSocket.emit("message", json)
            Log.e(TAG, "✅✅✅ 已发送消息到服务器 - messageId=${messageDto.messageId}, conversationId=${messageDto.conversationId}, senderId=${messageDto.senderId}, receiverId=${messageDto.receiverId}, content=${messageDto.content.take(50)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ 发送消息失败", e)
            e.printStackTrace()
            false
        }
    }
    
    fun disconnect() {
        Log.d(TAG, "=== WebSocketManager.disconnect() 被调用 ===")
        synchronized(this) {
            socket?.let { s ->
                try {
                    val socketId = s.id()
                    Log.d(TAG, "准备断开WebSocket - socketId: $socketId")
                    // 移除所有事件监听器
                    s.off()
                    Log.d(TAG, "已移除所有事件监听器")
                    // 断开连接
                    s.disconnect()
                    Log.d(TAG, "WebSocket已断开 - socketId: $socketId")
                } catch (e: Exception) {
                    Log.e(TAG, "断开WebSocket时出错", e)
                }
            }
        socket = null
            Log.d(TAG, "WebSocket实例已清空")
        }
    }
    
    /**
     * 检查WebSocket是否已连接
     */
    fun isConnected(): Boolean {
        return socket != null && socket!!.connected()
    }
    
    private fun parseMessage(json: JSONObject): MessageDto? {
        return try {
            MessageDto(
                messageId = json.getString("messageId"),
                conversationId = json.getString("conversationId"),
                senderId = json.getString("senderId"),
                receiverId = json.getString("receiverId"),
                content = json.getString("content"),
                messageType = MessageType.valueOf(json.getString("messageType")),
                timestamp = json.getLong("timestamp"),
                extra = json.optString("extra").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
            null
        }
    }
    
    sealed class ConnectionState {
        object Connected : ConnectionState()
        data class Disconnected(val reason: String) : ConnectionState()
        data class MessageReceived(val message: MessageDto) : ConnectionState()
        data class MessageRecalled(val messageId: String) : ConnectionState()
        data class FriendRequestReceived(
            val requestId: String,
            val fromUserId: String,
            val fromUserNickname: String,
            val fromUserAvatar: String?,
            val message: String?,
            val timestamp: Long
        ) : ConnectionState()
        data class AccountKicked(
            val reason: String,
            val message: String
        ) : ConnectionState()
        data class VoiceCallOffer(
            val fromUserId: String,
            val toUserId: String,
            val timestamp: Long
        ) : ConnectionState()
    }
}
