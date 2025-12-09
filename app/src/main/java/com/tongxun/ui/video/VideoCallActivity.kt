package com.tongxun.ui.video

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tongxun.databinding.ActivityVideoCallBinding
import com.tongxun.webrtc.WebRTCManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.webrtc.*

@AndroidEntryPoint
class VideoCallActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityVideoCallBinding
    private val viewModel: VideoCallViewModel by viewModels()
    
    private lateinit var webRTCManager: WebRTCManager
    private var ringtonePlayer: MediaPlayer? = null
    
    private val targetUserId: String by lazy {
        intent.getStringExtra("target_user_id") ?: ""
    }
    private val targetUserName: String by lazy {
        intent.getStringExtra("target_user_name") ?: "用户"
    }
    private val isIncoming: Boolean by lazy {
        intent.getBooleanExtra("is_incoming", false)
    }
    
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initializeCall()
        } else {
            Toast.makeText(this, "需要摄像头和麦克风权限", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 检查权限
        if (!hasPermissions()) {
            requestPermissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
            return
        }
        
        setupUI()
        initializeCall()
        setupObservers()
    }
    
    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun setupUI() {
        binding.tvCallerName.text = targetUserName
        
        if (isIncoming) {
            // 来电界面
            binding.incomingCallLayout.visibility = View.VISIBLE
            binding.topInfoLayout.visibility = View.GONE
            binding.bottomControlsLayout.visibility = View.GONE
            binding.localVideoView.visibility = View.GONE
            binding.remoteVideoView.visibility = View.GONE
            
            binding.tvIncomingCallerName.text = targetUserName
            binding.tvIncomingCallStatus.text = "视频通话"
            
            // 播放来电铃声
            playCallRingtone()
        } else {
            // 发起通话界面
            binding.incomingCallLayout.visibility = View.GONE
            binding.topInfoLayout.visibility = View.VISIBLE
            binding.bottomControlsLayout.visibility = View.VISIBLE
            binding.localVideoView.visibility = View.VISIBLE
            binding.remoteVideoView.visibility = View.VISIBLE
            
            binding.tvCallStatus.text = "正在呼叫..."
            
            // 播放去电铃声（等待对方接听）
            playCallRingtone()
        }
        
        // 控制按钮
        binding.btnHangup.setOnClickListener {
            viewModel.hangup()
            finish()
        }
        
        binding.btnAccept.setOnClickListener {
            stopRingtone()
            viewModel.acceptCall()
            binding.incomingCallLayout.visibility = View.GONE
            binding.topInfoLayout.visibility = View.VISIBLE
            binding.bottomControlsLayout.visibility = View.VISIBLE
            binding.localVideoView.visibility = View.VISIBLE
            binding.remoteVideoView.visibility = View.VISIBLE
        }
        
        binding.btnReject.setOnClickListener {
            stopRingtone()
            viewModel.rejectCall()
            finish()
        }
        
        binding.btnMute.setOnClickListener {
            viewModel.toggleMute()
        }
        
        binding.btnSwitchCamera.setOnClickListener {
            viewModel.switchCamera()
        }
        
        binding.btnToggleVideo.setOnClickListener {
            viewModel.toggleVideo()
        }
    }
    
    private fun initializeCall() {
        // 初始化 WebRTC
        webRTCManager = WebRTCManager(this)
        webRTCManager.initialize()
        
        // 创建 PeerConnection
        webRTCManager.createPeerConnection(
            binding.localVideoView,
            binding.remoteVideoView
        )
        
        // 设置回调
        webRTCManager.onSdpCreatedCallback = { sdp ->
            viewModel.sendSdp(sdp, targetUserId)
        }
        
        webRTCManager.onIceCandidateCallback = { candidate ->
            viewModel.sendIceCandidate(candidate, targetUserId)
        }
        
        if (isIncoming) {
            // 接收方：等待 Offer
            viewModel.waitForOffer(targetUserId)
        } else {
            // 发起方：创建 Offer
            webRTCManager.startLocalVideo()
            webRTCManager.startLocalAudio()
            webRTCManager.createOffer()
            viewModel.initiateCall(targetUserId)
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.callState.collect { state ->
                when (state) {
                    is VideoCallViewModel.CallState.Connecting -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvCallStatus.text = "正在连接..."
                    }
                    is VideoCallViewModel.CallState.Connected -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvCallStatus.text = "通话中"
                        // 通话连接后停止铃声
                        stopRingtone()
                    }
                    is VideoCallViewModel.CallState.Ended -> {
                        finish()
                    }
                    is VideoCallViewModel.CallState.Error -> {
                        Toast.makeText(this@VideoCallActivity, state.message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.isMuted.collect { muted ->
                binding.btnMute.alpha = if (muted) 0.5f else 1.0f
            }
        }
        
        lifecycleScope.launch {
            viewModel.isVideoEnabled.collect { enabled ->
                binding.btnToggleVideo.alpha = if (enabled) 1.0f else 0.5f
                binding.localVideoView.visibility = if (enabled) View.VISIBLE else View.GONE
            }
        }
        
        // 监听远程 SDP 和 ICE Candidate
        viewModel.onRemoteSdpReceived = { sdp ->
            webRTCManager.setRemoteDescription(sdp)
        }
        
        viewModel.onRemoteIceCandidateReceived = { candidate ->
            webRTCManager.addIceCandidate(candidate)
        }
    }
    
    private fun playCallRingtone() {
        try {
            // 使用系统默认铃声
            val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            ringtonePlayer = MediaPlayer.create(this, ringtoneUri).apply {
                isLooping = true
                setVolume(1.0f, 1.0f)
                start()
            }
            Log.d("VideoCallActivity", "开始播放通话铃声 (${if (isIncoming) "来电" else "去电"})")
        } catch (e: Exception) {
            Log.e("VideoCallActivity", "播放通话铃声失败", e)
        }
    }
    
    private fun stopRingtone() {
        try {
            ringtonePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            ringtonePlayer = null
            Log.d("VideoCallActivity", "停止播放通话铃声")
        } catch (e: Exception) {
            Log.e("VideoCallActivity", "停止通话铃声失败", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
        webRTCManager.release()
    }
}

