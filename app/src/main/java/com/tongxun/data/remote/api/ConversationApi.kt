package com.tongxun.data.remote.api

import com.tongxun.data.remote.dto.ConversationDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface ConversationApi {
    
    @GET("conversations")
    suspend fun getConversations(): List<ConversationDto>
    
    @DELETE("conversations/{conversationId}")
    suspend fun deleteConversation(@Path("conversationId") conversationId: String): retrofit2.Response<Unit>
}

