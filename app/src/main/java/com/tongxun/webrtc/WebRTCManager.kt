package com.tongxun.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.util.*

/**
 * PeerConnection Observer 基类
 */
abstract class PeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: SignalingState) {}
    override fun onIceConnectionChange(state: IceConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: IceGatheringState) {}
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onAddStream(stream: MediaStream) {}
    override fun onRemoveStream(stream: MediaStream) {}
    override fun onDataChannel(channel: DataChannel) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
}

/**
 * WebRTC 管理器
 * 负责管理 WebRTC 连接、信令处理和媒体流
 */
class WebRTCManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WebRTCManager"
        
        // STUN 服务器配置（用于 NAT 穿透）
        private val ICE_SERVERS = listOf(
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoRenderer: SurfaceViewRenderer? = null
    private var remoteVideoRenderer: SurfaceViewRenderer? = null
    private var eglBase: EglBase? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted
    
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled
    
    /**
     * 初始化 WebRTC
     */
    fun initialize() {
        try {
            // 初始化 PeerConnectionFactory
            val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initializationOptions)
            
            // 创建 EglBase
            eglBase = EglBase.create()
            
            val options = PeerConnectionFactory.Options()
            val encoderFactory = DefaultVideoEncoderFactory(
                eglBase!!.eglBaseContext,
                true,
                true
            )
            val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
            
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            
            Log.d(TAG, "WebRTC 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC 初始化失败", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "初始化失败")
        }
    }
    
    /**
     * 创建 PeerConnection
     */
    fun createPeerConnection(
        localRenderer: SurfaceViewRenderer,
        remoteRenderer: SurfaceViewRenderer
    ) {
        this.localVideoRenderer = localRenderer
        this.remoteVideoRenderer = remoteRenderer
        
        // 初始化渲染器
        localRenderer.init(eglBase!!.eglBaseContext, null)
        remoteRenderer.init(eglBase!!.eglBaseContext, null)
        
        val rtcConfig = RTCConfiguration(ICE_SERVERS)
        rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    Log.d(TAG, "ICE Candidate: ${iceCandidate.sdp}")
                    onIceCandidateCallback?.invoke(iceCandidate)
                }
                
                override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                    Log.d(TAG, "ICE Connection State: $iceConnectionState")
                    when (iceConnectionState) {
                        IceConnectionState.CONNECTED -> {
                            _connectionState.value = ConnectionState.Connected
                        }
                        IceConnectionState.DISCONNECTED -> {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                        IceConnectionState.FAILED -> {
                            _connectionState.value = ConnectionState.Error("连接失败")
                        }
                        else -> {}
                    }
                }
                
                override fun onTrack(rtcTrackEvent: RtpTransceiver?) {
                    Log.d(TAG, "收到远程轨道")
                    rtcTrackEvent?.receiver?.track()?.let { track ->
                        if (track is VideoTrack) {
                            track.addSink(remoteVideoRenderer)
                        } else if (track is AudioTrack) {
                            track.setVolume(1.0)
                        }
                    }
                }
            }
        )
    }
    
    /**
     * 开始本地视频捕获
     * @param useFrontCamera 是否使用前置摄像头，默认为 true
     */
    fun startLocalVideo(useFrontCamera: Boolean = true) {
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            
            if (deviceNames.isEmpty()) {
                Log.e(TAG, "未找到摄像头设备")
                return
            }
            
            // 查找前置或后置摄像头
            val targetDeviceName = if (useFrontCamera) {
                // 优先查找前置摄像头
                deviceNames.find { enumerator.isFrontFacing(it) }
                    ?: deviceNames.firstOrNull() // 如果没找到前置，使用第一个
            } else {
                // 查找后置摄像头
                deviceNames.find { !enumerator.isFrontFacing(it) }
                    ?: deviceNames.firstOrNull() // 如果没找到后置，使用第一个
            }
            
            if (targetDeviceName == null) {
                Log.e(TAG, "未找到可用的摄像头设备")
                return
            }
            
            videoCapturer = enumerator.createCapturer(targetDeviceName, null)
            Log.d(TAG, "使用摄像头: $targetDeviceName (前置: ${enumerator.isFrontFacing(targetDeviceName)})")
            
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread",
                eglBase!!.eglBaseContext
            )
            
            val videoSource = peerConnectionFactory?.createVideoSource(false)
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            videoCapturer?.startCapture(640, 480, 30)
            
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track", videoSource)
            localVideoTrack?.addSink(localVideoRenderer)
            
            // 添加到 PeerConnection
            peerConnection?.addTrack(localVideoTrack)
            
            Log.d(TAG, "本地视频已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动本地视频失败", e)
        }
    }
    
    /**
     * 开始本地音频捕获
     */
    fun startLocalAudio() {
        try {
            val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
            localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
            peerConnection?.addTrack(localAudioTrack)
            
            Log.d(TAG, "本地音频已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动本地音频失败", e)
        }
    }
    
    /**
     * 创建 Offer（发起方）
     */
    fun createOffer() {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "创建 Offer 成功")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "设置本地描述成功")
                        onSdpCreatedCallback?.invoke(sdp)
                    }
                    
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "设置本地描述失败: $error")
                    }
                    
                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "创建 Offer 失败: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, MediaConstraints())
    }
    
    /**
     * 创建 Answer（接收方）
     */
    fun createAnswer() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "创建 Answer 成功")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "设置本地描述成功")
                        onSdpCreatedCallback?.invoke(sdp)
                    }
                    
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "设置本地描述失败: $error")
                    }
                    
                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "创建 Answer 失败: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, MediaConstraints())
    }
    
    /**
     * 设置远程描述
     */
    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "设置远程描述成功")
                if (sdp.type == SessionDescription.Type.OFFER) {
                    createAnswer()
                }
            }
            
            override fun onSetFailure(error: String) {
                Log.e(TAG, "设置远程描述失败: $error")
            }
            
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, sdp)
    }
    
    /**
     * 添加 ICE Candidate
     */
    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }
    
    /**
     * 切换静音
     */
    fun toggleMute() {
        val newMuted = !_isMuted.value
        localAudioTrack?.setEnabled(!newMuted)
        _isMuted.value = newMuted
    }
    
    /**
     * 切换摄像头
     */
    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }
    
    /**
     * 切换视频开关
     */
    fun toggleVideo() {
        val newEnabled = !_isVideoEnabled.value
        localVideoTrack?.setEnabled(newEnabled)
        _isVideoEnabled.value = newEnabled
    }
    
    /**
     * 释放资源
     */
    fun release() {
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
        
        localVideoRenderer?.release()
        remoteVideoRenderer?.release()
        eglBase?.release()
        
        Log.d(TAG, "WebRTC 资源已释放")
    }
    
    // 回调接口
    var onSdpCreatedCallback: ((SessionDescription) -> Unit)? = null
    var onIceCandidateCallback: ((IceCandidate) -> Unit)? = null
    
    /**
     * 连接状态
     */
    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
