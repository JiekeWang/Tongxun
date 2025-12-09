package com.tongxun.ui.video

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tongxun.data.remote.WebSocketManager
import com.tongxun.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import javax.inject.Inject

@HiltViewModel
class VideoCallViewModel @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "VideoCallViewModel"
    }
    
    private val _callState = MutableStateFlow<CallState>(CallState.Connecting)
    val callState: StateFlow<CallState> = _callState
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted
    
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled
    
    var onRemoteSdpReceived: ((SessionDescription) -> Unit)? = null
    var onRemoteIceCandidateReceived: ((IceCandidate) -> Unit)? = null
    
    /**
     * 发起视频通话
     */
    fun initiateCall(targetUserId: String) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                val currentUserId = currentUser?.userId
                if (currentUserId == null) {
                    _callState.value = CallState.Error("未登录")
                    return@launch
                }
                
                val data = JSONObject().apply {
                    put("targetUserId", targetUserId)
                    put("callType", "video")
                }
                
                val success = webSocketManager.emitEvent("video_call", data)
                if (success) {
                    Log.d(TAG, "已发送视频通话请求")
                } else {
                    _callState.value = CallState.Error("发送失败，请检查网络连接")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发起视频通话失败", e)
                _callState.value = CallState.Error(e.message ?: "发起通话失败")
            }
        }
    }
    
    /**
     * 等待 Offer（接收方）
     */
    fun waitForOffer(targetUserId: String) {
        // 监听 WebSocket 事件
        // 实际实现中，应该在 WebSocketManager 中注册事件监听器
    }
    
    /**
     * 发送 SDP
     */
    fun sendSdp(sdp: SessionDescription, targetUserId: String) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                val currentUserId = currentUser?.userId
                if (currentUserId == null) {
                    _callState.value = CallState.Error("未登录")
                    return@launch
                }
                
                val sdpData = JSONObject().apply {
                    put("type", sdp.type.canonicalForm())
                    put("sdp", sdp.description)
                }
                
                val data = JSONObject().apply {
                    put("targetUserId", targetUserId)
                    put("sdp", sdpData)
                }
                
                val success = webSocketManager.emitEvent("video_call_sdp", data)
                if (success) {
                    Log.d(TAG, "已发送 SDP: ${sdp.type}")
                } else {
                    Log.e(TAG, "发送 SDP 失败，WebSocket 未连接")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送 SDP 失败", e)
                _callState.value = CallState.Error(e.message ?: "发送 SDP 失败")
            }
        }
    }
    
    /**
     * 发送 ICE Candidate
     */
    fun sendIceCandidate(candidate: IceCandidate, targetUserId: String) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                val currentUserId = currentUser?.userId
                if (currentUserId == null) {
                    return@launch
                }
                
                val iceCandidate = JSONObject().apply {
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                }
                
                val data = JSONObject().apply {
                    put("targetUserId", targetUserId)
                    put("iceCandidate", iceCandidate)
                }
                
                val success = webSocketManager.emitEvent("video_call_ice", data)
                if (success) {
                    Log.d(TAG, "已发送 ICE Candidate")
                } else {
                    Log.e(TAG, "发送 ICE Candidate 失败，WebSocket 未连接")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送 ICE Candidate 失败", e)
            }
        }
    }
    
    /**
     * 接听通话
     */
    fun acceptCall() {
        _callState.value = CallState.Connecting
        // 实际实现中，应该通过 WebSocket 发送接听消息
    }
    
    /**
     * 拒绝通话
     */
    fun rejectCall() {
        _callState.value = CallState.Ended
        // 实际实现中，应该通过 WebSocket 发送拒绝消息
    }
    
    /**
     * 挂断通话
     */
    fun hangup() {
        _callState.value = CallState.Ended
        // 实际实现中，应该通过 WebSocket 发送挂断消息
    }
    
    /**
     * 切换静音
     */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }
    
    /**
     * 切换摄像头
     */
    fun switchCamera() {
        // 实际实现中，应该在 WebRTCManager 中切换摄像头
    }
    
    /**
     * 切换视频开关
     */
    fun toggleVideo() {
        _isVideoEnabled.value = !_isVideoEnabled.value
    }
    
    /**
     * 通话状态
     */
    sealed class CallState {
        object Connecting : CallState()
        object Connected : CallState()
        object Ended : CallState()
        data class Error(val message: String) : CallState()
    }
}

