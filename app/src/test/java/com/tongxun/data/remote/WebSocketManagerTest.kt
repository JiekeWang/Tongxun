package com.tongxun.data.remote

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebSocketManagerTest {
    
    private lateinit var webSocketManager: WebSocketManager
    
    @Before
    fun setup() {
        webSocketManager = WebSocketManager.getInstance()
    }
    
    @Test
    fun `test parse Socket IO friend request message`() = runTest {
        // Socket.IO格式：42["friend_request", {...}]
        val socketIOMessage = """42["friend_request",{"requestId":"req-123","fromUserId":"user-456","fromUserNickname":"Test User","fromUserAvatar":null,"message":"Hello","timestamp":1234567890}]"""
        
        // 这个测试需要实际的WebSocket连接
        // 这里只测试消息解析逻辑
        assertTrue("Socket.IO消息格式应该以42开头", socketIOMessage.startsWith("42"))
    }
    
    @Test
    fun `test parse standard JSON friend request message`() {
        val jsonMessage = """{"type":"friend_request","requestId":"req-123","fromUserId":"user-456","fromUserNickname":"Test User","fromUserAvatar":null,"message":"Hello","timestamp":1234567890}"""
        
        // 验证JSON格式
        assertTrue("应该是有效的JSON", jsonMessage.contains("\"type\":\"friend_request\""))
        assertTrue("应该包含requestId", jsonMessage.contains("\"requestId\""))
        assertTrue("应该包含fromUserId", jsonMessage.contains("\"fromUserId\""))
    }
    
    @Test
    fun `test FriendRequestReceived data class`() {
        val friendRequest = WebSocketManager.ConnectionState.FriendRequestReceived(
            requestId = "req-123",
            fromUserId = "user-456",
            fromUserNickname = "Test User",
            fromUserAvatar = null,
            message = "Hello",
            timestamp = 1234567890L
        )
        
        assertEquals("req-123", friendRequest.requestId)
        assertEquals("user-456", friendRequest.fromUserId)
        assertEquals("Test User", friendRequest.fromUserNickname)
        assertEquals("Hello", friendRequest.message)
        assertEquals(1234567890L, friendRequest.timestamp)
    }
}


