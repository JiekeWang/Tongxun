package com.tongxun.data.repository

import com.tongxun.data.remote.api.FriendApi
import com.tongxun.data.remote.dto.FriendRequestsResponse
import com.tongxun.data.remote.dto.SendFriendRequestDto
import com.tongxun.data.local.TongxunDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.mockito.kotlin.mock
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class FriendRepositoryTest {
    
    @Mock
    private lateinit var friendApi: FriendApi
    
    @Mock
    private lateinit var database: TongxunDatabase
    
    @Mock
    private lateinit var userRepository: com.tongxun.domain.repository.UserRepository
    
    private lateinit var friendRepository: FriendRepositoryImpl
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        friendRepository = FriendRepositoryImpl(friendApi, database, userRepository)
    }
    
    @Test
    fun `test addFriend success`() = runTest {
        // Given
        val userId = "user-123"
        val friendId = "user-456"
        val message = "Hello, friend!"
        
        doNothing().whenever(friendApi).sendFriendRequest(any())
        
        // When
        val result = friendRepository.addFriend(userId, friendId, message)
        
        // Then
        assertTrue(result.isSuccess)
        verify(friendApi).sendFriendRequest(
            SendFriendRequestDto(
                toUserId = friendId,
                message = message
            )
        )
    }
    
    @Test
    fun `test addFriend with blank userId should fail`() = runTest {
        // Given
        val userId = ""
        val friendId = "user-456"
        
        // When
        val result = friendRepository.addFriend(userId, friendId, null)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("用户ID不能为空", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `test addFriend with same userId should fail`() = runTest {
        // Given
        val userId = "user-123"
        val friendId = "user-123"
        
        // When
        val result = friendRepository.addFriend(userId, friendId, null)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("不能添加自己为好友", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `test addFriend with HTTP error`() = runTest {
        // Given
        val userId = "user-123"
        val friendId = "user-456"
        val errorResponse = Response.error<String>(400, okhttp3.ResponseBody.create(null, """{"error":"已经是好友关系"}"""))
        val httpException = HttpException(errorResponse)
        
        whenever(friendApi.sendFriendRequest(any())).thenThrow(httpException)
        
        // When
        val result = friendRepository.addFriend(userId, friendId, null)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HttpException)
    }
    
    @Test
    fun `test getFriendRequests success`() = runTest {
        // Given
        val response = FriendRequestsResponse(
            received = emptyList(),
            sent = emptyList()
        )
        
        whenever(friendApi.getFriendRequests()).thenReturn(response)
        
        // When
        val result = friendRepository.getFriendRequests()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(response, result.getOrNull())
    }
    
    @Test
    fun `test getFriendRequests with network error`() = runTest {
        // Given
        whenever(friendApi.getFriendRequests()).thenThrow(IOException("Network error"))
        
        // When
        val result = friendRepository.getFriendRequests()
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }
}


