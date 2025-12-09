package com.tongxun.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.local.entity.ConversationEntity
import com.tongxun.domain.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "HomeViewModel"
    }
    
    init {
        Log.d(TAG, "HomeViewModel.init() - 初始化")
    }
    
    val conversations: StateFlow<List<ConversationEntity>> = conversationRepository
        .getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        .also { stateFlow ->
            // 添加日志监听（不影响原始Flow）
            viewModelScope.launch {
                stateFlow.collect { conversations ->
                    Log.d(TAG, "✅ conversations Flow 更新 - 共 ${conversations.size} 个会话")
                    if (conversations.isNotEmpty()) {
                        conversations.take(3).forEach { conversation ->
                            Log.d(TAG, "  会话: ${conversation.conversationId.take(8)}..., 目标: ${conversation.targetName}, 最后消息: ${conversation.lastMessage?.take(20)}")
                        }
                    } else {
                        Log.w(TAG, "⚠️ conversations 列表为空")
                    }
                }
            }
        }
    
    fun setTopStatus(conversationId: String, isTop: Boolean) {
        viewModelScope.launch {
            conversationRepository.setTop(conversationId, isTop)
        }
    }
    
    fun setMutedStatus(conversationId: String, isMuted: Boolean) {
        viewModelScope.launch {
            conversationRepository.setMuted(conversationId, isMuted)
        }
    }
    
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                Log.e(TAG, "🔥🔥🔥 开始删除会话 - conversationId: $conversationId")
                conversationRepository.deleteConversation(conversationId)
                Log.e(TAG, "✅ 会话删除完成 - conversationId: $conversationId")
                
                // 🔥 关键修复：删除后延迟一下再同步，确保Room数据库的删除操作完成
                // 同时，删除后同步是为了清理可能存在的其他不一致数据
                kotlinx.coroutines.delay(500) // 等待500ms，确保删除操作完成
                
                // 删除后同步会话列表，确保服务器端也删除，并清理本地可能存在的其他不一致数据
                // 注意：deleteConversation 内部已经会删除服务器端记录，这里同步是为了确保一致性
                conversationRepository.syncConversationsFromServer()
                    .onSuccess {
                        Log.e(TAG, "✅ 删除后同步会话列表成功")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "❌ 删除后同步会话列表失败", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ 删除会话异常", e)
                // 即使删除失败，也尝试同步一次，确保数据一致性
                try {
                    kotlinx.coroutines.delay(500)
                    conversationRepository.syncConversationsFromServer()
                        .onSuccess {
                            Log.e(TAG, "✅ 异常后同步会话列表成功")
                        }
                        .onFailure { error ->
                            Log.e(TAG, "❌ 异常后同步会话列表失败", error)
                        }
                } catch (syncError: Exception) {
                    Log.e(TAG, "❌ 同步会话列表异常", syncError)
                }
            }
        }
    }
}

