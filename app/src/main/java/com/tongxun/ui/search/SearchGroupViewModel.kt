package com.tongxun.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.remote.dto.GroupDto
import com.tongxun.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchGroupUiState())
    val uiState: StateFlow<SearchGroupUiState> = _uiState.asStateFlow()
    
    fun updateSearchText(text: String) {
        _uiState.value = _uiState.value.copy(
            searchText = text,
            searchResults = emptyList()
        )
    }
    
    fun searchGroups(searchText: String) {
        val trimmedText = searchText.trim()
        
        if (trimmedText.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "搜索内容不能为空"
            )
            return
        }
        
        if (_uiState.value.isLoading) {
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null
                )
                
                groupRepository.searchGroups(trimmedText)
                    .onSuccess { groups ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            searchResults = groups,
                            error = null
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "搜索失败"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "搜索异常"
                )
            }
        }
    }
    
    fun applyToJoinGroup(groupId: String, message: String? = null) {
        viewModelScope.launch {
            try {
                groupRepository.applyToJoinGroup(groupId, message)
                    .onSuccess { requestId ->
                        _uiState.value = _uiState.value.copy(
                            applyResult = "申请已提交，等待审核",
                            error = null
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            error = exception.message ?: "申请失败"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "申请异常"
                )
            }
        }
    }
    
    fun joinGroup(groupId: String) {
        viewModelScope.launch {
            try {
                groupRepository.joinGroup(groupId)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            applyResult = "加入成功",
                            error = null
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            error = exception.message ?: "加入失败"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "加入异常"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearApplyResult() {
        _uiState.value = _uiState.value.copy(applyResult = null)
    }
    
    data class SearchGroupUiState(
        val isLoading: Boolean = false,
        val searchText: String = "",
        val searchResults: List<GroupDto> = emptyList(),
        val error: String? = null,
        val applyResult: String? = null
    )
}

