package com.tongxun.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.local.entity.FriendEntity
import com.tongxun.data.remote.dto.GroupDto
import com.tongxun.data.remote.dto.GroupMemberDto
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.domain.repository.ConversationRepository
import com.tongxun.domain.repository.FriendRepository
import com.tongxun.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val friendRepository: FriendRepository,
    private val authRepository: AuthRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    
    private val TAG = "GroupInfoViewModel"
    
    private val _groupInfo = MutableStateFlow<GroupDto?>(null)
    val groupInfo: StateFlow<GroupDto?> = _groupInfo.asStateFlow()
    
    private val _members = MutableStateFlow<List<GroupMemberDto>>(emptyList())
    val members: StateFlow<List<GroupMemberDto>> = _members.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _addMembersResult = MutableStateFlow<Result<Int>?>(null)
    val addMembersResult: StateFlow<Result<Int>?> = _addMembersResult.asStateFlow()
    
    private val _removeMemberResult = MutableStateFlow<Result<Unit>?>(null)
    val removeMemberResult: StateFlow<Result<Unit>?> = _removeMemberResult.asStateFlow()
    
    private val _disbandGroupResult = MutableStateFlow<Result<Unit>?>(null)
    val disbandGroupResult: StateFlow<Result<Unit>?> = _disbandGroupResult.asStateFlow()
    
    private val _isDisbanded = MutableStateFlow(false)
    val isDisbanded: StateFlow<Boolean> = _isDisbanded.asStateFlow()
    
    val currentUserId: String?
        get() = authRepository.getCurrentUser()?.userId
    
    val isOwner: Boolean
        get() = _groupInfo.value?.ownerId == currentUserId
    
    val isAdmin: Boolean
        get() = _members.value.any { it.userId == currentUserId && (it.role == "OWNER" || it.role == "ADMIN") }
    
    fun loadGroupInfo(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // 加载群组信息
                groupRepository.getGroupInfo(groupId)
                    .onSuccess { group ->
                        _groupInfo.value = group
                        _isDisbanded.value = false
                        _error.value = null // 清除之前的错误
                        android.util.Log.d(TAG, "群组信息加载成功: ${group.groupName}")
                    }
                    .onFailure { e ->
                        // 如果获取群组信息失败（可能是群组已解散），标记为已解散
                        _isDisbanded.value = true
                        // 对于 404 错误（群组不存在），不显示错误提示，直接显示删除会话按钮
                        val is404Error = e.message?.contains("404") == true || e.message?.contains("Not Found") == true
                        if (!is404Error) {
                            _error.value = e.message ?: "加载群组信息失败"
                        } else {
                            _error.value = null // 404 错误不显示，因为这是预期的（群组已解散）
                            android.util.Log.d(TAG, "群组不存在（已解散），不显示错误提示")
                        }
                        android.util.Log.e(TAG, "加载群组信息失败，可能群组已解散", e)
                    }
                
                // 加载群成员列表
                groupRepository.getGroupMembers(groupId)
                    .onSuccess { memberList ->
                        _members.value = memberList
                        android.util.Log.d(TAG, "群成员列表加载成功: ${memberList.size} 人")
                    }
                    .onFailure { e ->
                        _error.value = e.message ?: "加载群成员列表失败"
                        android.util.Log.e(TAG, "加载群成员列表失败", e)
                    }
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
                android.util.Log.e(TAG, "加载失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshGroupInfo(groupId: String) {
        loadGroupInfo(groupId)
    }
    
    fun addMembers(groupId: String, memberIds: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _addMembersResult.value = null
            
            try {
                // 在 ViewModel 层也进行验证
                if (memberIds.isEmpty()) {
                    android.util.Log.e(TAG, "❌ ViewModel: memberIds 为空")
                    _addMembersResult.value = Result.failure(Exception("请至少选择一个好友"))
                    _isLoading.value = false
                    return@launch
                }
                
                android.util.Log.d(TAG, "ViewModel: 准备添加成员 - groupId: $groupId, memberIds: $memberIds, 数量: ${memberIds.size}")
                val result = groupRepository.addMembersToGroup(groupId, memberIds)
                _addMembersResult.value = result
                
                if (result.isSuccess) {
                    // 刷新群组信息和成员列表
                    refreshGroupInfo(groupId)
                }
            } catch (e: Exception) {
                _addMembersResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun removeMember(groupId: String, memberId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _removeMemberResult.value = null
            
            try {
                val result = groupRepository.removeMember(groupId, memberId)
                _removeMemberResult.value = result
                
                if (result.isSuccess) {
                    // 刷新群组信息和成员列表
                    refreshGroupInfo(groupId)
                }
            } catch (e: Exception) {
                _removeMemberResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun disbandGroup(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _disbandGroupResult.value = null
            
            try {
                val result = groupRepository.disbandGroup(groupId)
                _disbandGroupResult.value = result
            } catch (e: Exception) {
                _disbandGroupResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearAddMembersResult() {
        _addMembersResult.value = null
    }
    
    fun clearRemoveMemberResult() {
        _removeMemberResult.value = null
    }
    
    fun clearDisbandGroupResult() {
        _disbandGroupResult.value = null
    }
    
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                conversationRepository.deleteConversation(conversationId)
                android.util.Log.d(TAG, "会话删除成功 - conversationId: $conversationId")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "删除会话失败", e)
                _error.value = e.message ?: "删除会话失败"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 获取可添加的好友列表（排除已经是群成员的好友）
     */
    suspend fun getAvailableFriends(): List<FriendEntity> {
        val currentUser = authRepository.getCurrentUser() ?: return emptyList()
        val friendsFlow = friendRepository.getFriends(currentUser.userId)
        
        val friends = friendsFlow.first()
        val memberIds = _members.value.map { member -> member.userId }.toSet()
        return friends.filter { friend -> !memberIds.contains(friend.friendId) }
    }
}

