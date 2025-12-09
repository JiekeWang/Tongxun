package com.tongxun.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

/**
 * 语音录制工具类
 */
class AudioRecorder(private val context: Context, private val outputFile: File) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude
    
    /**
     * 开始录制
     */
    fun startRecording() {
        if (isRecording) return
        
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                
                prepare()
                start()
                
                isRecording = true
                _recordingState.value = RecordingState.Recording
            }
        } catch (e: IOException) {
            _recordingState.value = RecordingState.Error(e.message ?: "录制失败")
            release()
        }
    }
    
    /**
     * 停止录制
     */
    fun stopRecording(): File? {
        if (!isRecording) return null
        
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            _recordingState.value = RecordingState.Stopped
            
            if (outputFile.exists() && outputFile.length() > 0) {
                outputFile
            } else {
                null
            }
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error(e.message ?: "停止录制失败")
            release()
            null
        }
    }
    
    /**
     * 取消录制
     */
    fun cancelRecording() {
        stopRecording()
        outputFile.delete()
        _recordingState.value = RecordingState.Idle
    }
    
    /**
     * 获取当前振幅（用于显示波形）
     */
    fun getMaxAmplitude(): Int {
        return if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder!!.maxAmplitude
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }
    
    /**
     * 释放资源
     */
    private fun release() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaRecorder = null
        isRecording = false
    }
    
    /**
     * 录制状态
     */
    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        object Stopped : RecordingState()
        data class Error(val message: String) : RecordingState()
    }
}

