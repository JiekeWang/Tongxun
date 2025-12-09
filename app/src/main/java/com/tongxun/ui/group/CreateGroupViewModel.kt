package com.tongxun.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.local.entity.FriendEntity
import com.tongxun.data.remote.dto.GroupDto
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.domain.repository.FriendRepository
import com.tongxun.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val TAG = "CreateGroupViewModel"
    
    private val currentUser = authRepository.getCurrentUser()
    private val friendsFlow = if (currentUser != null) {
        android.util.Log.d(TAG, "初始化好友列表 - userId: ${currentUser.userId.take(8)}...")
        friendRepository.getFriends(currentUser.userId)
    } else {
        android.util.Log.w(TAG, "⚠️ 用户未登录，好友列表返回空")
        kotlinx.coroutines.flow.flowOf(emptyList())
    }
    
    private val selectedFriendIds = mutableSetOf<String>()
    
    private val _selectedFriends = MutableStateFlow<List<FriendEntity>>(emptyList())
    val selectedFriends: StateFlow<List<FriendEntity>> = _selectedFriends.asStateFlow()
    
    val friends: StateFlow<List<SelectableFriend>> = kotlinx.coroutines.flow.combine(
        friendsFlow,
        _selectedFriends
    ) { friendList, selectedList ->
        android.util.Log.d(TAG, "好友列表更新 - 原始好友数: ${friendList.size}, 已选择: ${selectedList.size}")
        val selectedIds = selectedList.map { it.friendId }.toSet()
        val result = friendList.map { friend ->
            SelectableFriend(
                friend = friend,
                isSelected = selectedIds.contains(friend.friendId)
            )
        }
        android.util.Log.d(TAG, "转换后的 SelectableFriend 数量: ${result.size}")
        result
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        .also { stateFlow ->
            // 添加日志监听
            viewModelScope.launch {
                stateFlow.collect { friends ->
                    android.util.Log.d(TAG, "✅ SelectableFriend 列表更新 - 共 ${friends.size} 个")
                    if (friends.isNotEmpty()) {
                        friends.take(3).forEach { friend ->
                            android.util.Log.d(TAG, "  好友: ${friend.friend.friendId.take(8)}..., 昵称: ${friend.friend.nickname ?: friend.friend.remark ?: "无"}, 已选择: ${friend.isSelected}")
                        }
                    } else {
                        android.util.Log.w(TAG, "⚠️ SelectableFriend 列表为空")
                    }
                }
            }
        }
    
    private val _createGroupResult = MutableStateFlow<Result<GroupDto>?>(null)
    val createGroupResult: StateFlow<Result<GroupDto>?> = _createGroupResult.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * 同步好友列表从服务器
     */
    fun syncFriends() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser == null) {
            android.util.Log.w(TAG, "同步好友列表失败：用户未登录")
            return
        }
        
        viewModelScope.launch {
            android.util.Log.d(TAG, "开始同步好友列表 - userId: ${currentUser.userId.take(8)}...")
            _isLoading.value = true
            try {
                friendRepository.syncFriendsFromServer(currentUser.userId)
                    .onSuccess {
                        android.util.Log.d(TAG, "✅ 好友列表同步成功")
                    }
                    .onFailure { e ->
                        android.util.Log.e(TAG, "❌ 好友列表同步失败: ${e.message}", e)
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun toggleFriendSelection(friendId: String) {
        val currentSelected = _selectedFriends.value.toMutableList()
        val friendToToggle = friends.value.find { it.friend.friendId == friendId }?.friend
        
        friendToToggle?.let { friend ->
            if (selectedFriendIds.contains(friendId)) {
                selectedFriendIds.remove(friendId)
                currentSelected.removeAll { it.friendId == friendId }
            } else {
                selectedFriendIds.add(friendId)
                currentSelected.add(friend)
            }
            _selectedFriends.value = currentSelected
        }
    }
    
    fun createGroup(groupName: String, description: String?, selectedFriends: List<FriendEntity>) {
        viewModelScope.launch {
            _isLoading.value = true
            _createGroupResult.value = null
            
            try {
                val memberIds = selectedFriends.map { it.friendId }
                val result = groupRepository.createGroup(groupName, description, memberIds)
                _createGroupResult.value = result
            } catch (e: Exception) {
                _createGroupResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
