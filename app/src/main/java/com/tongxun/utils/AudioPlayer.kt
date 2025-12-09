package com.tongxun.utils

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

/**
 * 语音播放工具类
 */
class AudioPlayer {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState
    
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress
    
    /**
     * 播放音频文件
     */
    fun play(file: File, onCompletion: (() -> Unit)? = null) {
        // 如果正在播放同一个文件，则暂停/恢复
        if (currentFile == file && mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }
        
        // 停止当前播放
        stop()
        
        currentFile = file
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    _playbackState.value = PlaybackState.Completed
                    onCompletion?.invoke()
                    // 播放完成后，释放资源但保留 currentFile 引用
                    // 这样可以通过文件比较来判断是否是重复播放
                    mediaPlayer?.release()
                    mediaPlayer = null
                    _playbackState.value = PlaybackState.Idle
                }
                
                setOnErrorListener { _, what, extra ->
                    _playbackState.value = PlaybackState.Error("播放失败: $what, $extra")
                    stop()
                    true
                }
                
                _playbackState.value = PlaybackState.Playing
            }
        } catch (e: IOException) {
            _playbackState.value = PlaybackState.Error(e.message ?: "播放失败")
            release()
        }
    }
    
    /**
     * 暂停播放
     */
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.value = PlaybackState.Paused
            }
        }
    }
    
    /**
     * 恢复播放
     */
    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _playbackState.value = PlaybackState.Playing
            }
        }
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
        }
        release()
        _playbackState.value = PlaybackState.Idle
    }
    
    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    /**
     * 获取总时长（毫秒）
     */
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }
    
    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    /**
     * 是否正在播放指定文件
     */
    fun isPlaying(file: File): Boolean {
        return currentFile == file && isPlaying()
    }
    
    /**
     * 释放资源
     */
    private fun release() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        // 注意：不清空 currentFile，以便判断是否是重复播放
        // currentFile = null
    }
    
    /**
     * 完全释放资源（包括文件引用）
     */
    fun releaseAll() {
        release()
        currentFile = null
    }
    
    /**
     * 播放状态
     */
    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Playing : PlaybackState()
        object Paused : PlaybackState()
        object Completed : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }
}

