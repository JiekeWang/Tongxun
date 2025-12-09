package com.tongxun.ui.contact

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tongxun.data.remote.dto.FriendRequestDto
import com.tongxun.databinding.ItemFriendRequestBinding
import java.text.SimpleDateFormat
import java.util.*

class FriendRequestAdapter(
    private val onAccept: (String) -> Unit,
    private val onReject: (String) -> Unit
) : ListAdapter<FriendRequestDto, FriendRequestAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemFriendRequestBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(request: FriendRequestDto) {
            binding.apply {
                // 显示昵称，如果没有昵称则显示用户ID
                tvName.text = request.nickname ?: request.fromUserId.takeIf { it.isNotBlank() } ?: "未知用户"
                tvMessage.text = request.message ?: "请求添加你为好友"
                tvTime.text = formatTime(request.createdAt)
                
                btnAccept.setOnClickListener {
                    try {
                        val requestId = request.requestId
                        if (requestId.isNotBlank()) {
                            android.util.Log.d("FriendRequestAdapter", "点击接受按钮 - requestId: $requestId")
                            onAccept(requestId)
                        } else {
                            android.util.Log.e("FriendRequestAdapter", "❌ requestId 为空，无法接受")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FriendRequestAdapter", "❌ 接受请求点击异常", e)
                    }
                }
                
                btnReject.setOnClickListener {
                    try {
                        val requestId = request.requestId
                        if (requestId.isNotBlank()) {
                            android.util.Log.d("FriendRequestAdapter", "点击拒绝按钮 - requestId: $requestId")
                            onReject(requestId)
                        } else {
                            android.util.Log.e("FriendRequestAdapter", "❌ requestId 为空，无法拒绝")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FriendRequestAdapter", "❌ 拒绝请求点击异常", e)
                    }
                }
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            try {
                // 如果时间戳为0或异常值，返回默认值
                if (timestamp <= 0) {
                    android.util.Log.w("FriendRequestAdapter", "时间戳无效: $timestamp")
                    return "未知时间"
                }
                
                val now = System.currentTimeMillis()
                val diff = now - timestamp
                
                // 如果时间戳是未来的时间，可能是秒而不是毫秒，尝试转换
                val actualTimestamp = if (diff < 0 && timestamp < 10000000000) {
                    // 可能是秒级时间戳，转换为毫秒
                    timestamp * 1000
                } else {
                    timestamp
                }
                
                val actualDiff = now - actualTimestamp
                
                return when {
                    actualDiff < 0 -> {
                        // 时间戳是未来的时间，显示日期
                        SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(actualTimestamp))
                    }
                    actualDiff < 60_000 -> "刚刚"
                    actualDiff < 3600_000 -> "${actualDiff / 60_000}分钟前"
                    actualDiff < 86400_000 -> "${actualDiff / 3600_000}小时前"
                    actualDiff < 604800_000 -> "${actualDiff / 86400_000}天前"
                    else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(actualTimestamp))
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendRequestAdapter", "格式化时间失败 - timestamp: $timestamp", e)
                return "未知时间"
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<FriendRequestDto>() {
        override fun areItemsTheSame(oldItem: FriendRequestDto, newItem: FriendRequestDto): Boolean {
            return oldItem.requestId == newItem.requestId
        }
        
        override fun areContentsTheSame(oldItem: FriendRequestDto, newItem: FriendRequestDto): Boolean {
            return oldItem == newItem
        }
    }
}

