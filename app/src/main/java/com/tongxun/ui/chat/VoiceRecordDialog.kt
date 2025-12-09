package com.tongxun.ui.chat

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tongxun.R
import com.tongxun.databinding.DialogVoiceRecordBinding
import com.tongxun.utils.AudioRecorder
import com.tongxun.utils.FileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 语音录制对话框
 */
class VoiceRecordDialog : DialogFragment() {
    
    private var _binding: DialogVoiceRecordBinding? = null
    private val binding get() = _binding!!
    
    private var audioRecorder: AudioRecorder? = null
    private var recordingFile: File? = null
    private var onRecordCompleteListener: ((File, Int) -> Unit)? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var recordingStartTime: Long = 0
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (audioRecorder?.recordingState?.value is AudioRecorder.RecordingState.Recording) {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                binding.tvDuration.text = "${elapsed}秒"
                binding.waveformView.alpha = 0.5f + (audioRecorder?.getMaxAmplitude()?.toFloat() ?: 0f) / 32768f * 0.5f
                handler.postDelayed(this, 100)
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogVoiceRecordBinding.inflate(layoutInflater)
        
        binding.btnRecord.setOnClickListener {
            startRecording()
        }
        
        binding.btnStop.setOnClickListener {
            stopRecording()
        }
        
        binding.btnCancel.setOnClickListener {
            cancelRecording()
            dismiss()
        }
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setTitle("录制语音")
            .setCancelable(false)
            .create()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
        audioRecorder?.cancelRecording()
        _binding = null
    }
    
    private fun startRecording() {
        try {
            // 创建临时文件
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cacheDir = FileManager.getCacheDir(requireContext())
            recordingFile = File(cacheDir, "voice_$timestamp.m4a")
            
            audioRecorder = AudioRecorder(requireContext(), recordingFile!!)
            audioRecorder?.startRecording()
            
            recordingStartTime = System.currentTimeMillis()
            handler.post(updateRunnable)
            
            binding.btnRecord.visibility = View.GONE
            binding.btnStop.visibility = View.VISIBLE
            binding.tvDuration.visibility = View.VISIBLE
            binding.waveformView.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "录制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopRecording() {
        android.util.Log.d("VoiceRecordDialog", "停止录音")
        val file = audioRecorder?.stopRecording()
        handler.removeCallbacks(updateRunnable)
        
        // 清空 audioRecorder 和 recordingFile，防止 onDestroyView() 删除文件
        // 因为文件已经传递给回调，需要保留用于上传
        audioRecorder = null
        recordingFile = null
        
        if (file != null && file.exists() && file.length() > 0) {
            val duration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            android.util.Log.d("VoiceRecordDialog", "录音文件存在 - 路径: ${file.absolutePath}, 大小: ${file.length()} bytes, 时长: $duration 秒")
            if (duration > 0) {
                android.util.Log.e("VoiceRecordDialog", "🔥🔥🔥 调用 onRecordCompleteListener - file: ${file.absolutePath}, duration: $duration")
                if (onRecordCompleteListener == null) {
                    android.util.Log.e("VoiceRecordDialog", "❌❌❌ onRecordCompleteListener 为 null！")
                    file.delete() // 如果没有 listener，删除文件
                    dismiss()
                } else {
                    android.util.Log.d("VoiceRecordDialog", "✅ onRecordCompleteListener 不为 null，准备调用")
                    // 先调用回调，然后再 dismiss，确保回调能够执行
                    // 注意：文件已经传递给回调，不应该在这里删除
                    try {
                        onRecordCompleteListener?.invoke(file, duration)
                        android.util.Log.d("VoiceRecordDialog", "✅ onRecordCompleteListener 调用完成")
                    } catch (e: Exception) {
                        android.util.Log.e("VoiceRecordDialog", "❌❌❌ 调用 onRecordCompleteListener 时发生异常", e)
                        // 如果回调失败，删除文件
                        file.delete()
                    }
                    // 延迟 dismiss，确保回调中的代码能够执行
                    // 文件由回调的接收者（ChatViewModel）负责清理
                    handler.postDelayed({
                        dismiss()
                    }, 500) // 增加延迟时间，确保上传开始
                }
            } else {
                android.util.Log.w("VoiceRecordDialog", "录制时间太短: $duration 秒")
                Toast.makeText(requireContext(), "录制时间太短", Toast.LENGTH_SHORT).show()
                file.delete()
                dismiss()
            }
        } else {
            android.util.Log.e("VoiceRecordDialog", "❌❌❌ 录制失败 - file: $file, exists: ${file?.exists()}, length: ${file?.length()}")
            Toast.makeText(requireContext(), "录制失败", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        
        binding.btnRecord.visibility = View.VISIBLE
        binding.btnStop.visibility = View.GONE
        binding.tvDuration.visibility = View.GONE
        binding.waveformView.visibility = View.GONE
    }
    
    private fun cancelRecording() {
        audioRecorder?.cancelRecording()
        recordingFile?.delete()
    }
    
    fun setOnRecordCompleteListener(listener: (File, Int) -> Unit) {
        android.util.Log.e("VoiceRecordDialog", "🔥🔥🔥 setOnRecordCompleteListener() 被调用")
        onRecordCompleteListener = listener
        android.util.Log.d("VoiceRecordDialog", "✅ listener 已设置，是否为 null: ${onRecordCompleteListener == null}")
    }
}

