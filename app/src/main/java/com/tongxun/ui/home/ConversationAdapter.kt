package com.tongxun.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tongxun.data.local.entity.ConversationEntity
import com.tongxun.data.local.entity.ConversationType
import com.tongxun.databinding.ItemConversationBinding
import com.tongxun.utils.ImageUrlUtils
import com.tongxun.utils.GroupAvatarGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(
    private val onItemClick: (ConversationEntity) -> Unit,
    private val onTopClick: ((ConversationEntity) -> Unit)? = null,
    private val onMutedClick: ((ConversationEntity) -> Unit)? = null,
    private val onDeleteClick: ((ConversationEntity) -> Unit)? = null,
    private val getGroupMemberAvatars: suspend (String) -> List<String?> = { emptyList() }
) : ListAdapter<ConversationEntity, ConversationAdapter.ViewHolder>(DiffCallback()) {
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
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
        private val binding: ItemConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(conversation: ConversationEntity) {
            binding.apply {
                tvName.text = conversation.targetName
                tvLastMessage.text = formatMessagePreview(conversation)
                tvTime.text = formatTime(conversation.lastMessageTime)
                
                // 显示未读数 - 大于0显示数字，0时不显示
                if (conversation.unreadCount > 0) {
                    tvUnreadCount.visibility = android.view.View.VISIBLE
                    if (conversation.unreadCount > 99) {
                        tvUnreadCount.text = "99+"
                        // 99+时调整宽度
                        tvUnreadCount.minWidth = android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_DIP, 26f,
                            binding.root.context.resources.displayMetrics
                        ).toInt()
                    } else {
                        tvUnreadCount.text = conversation.unreadCount.toString()
                        tvUnreadCount.minWidth = android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_DIP, 18f,
                            binding.root.context.resources.displayMetrics
                        ).toInt()
                    }
                } else {
                    tvUnreadCount.visibility = android.view.View.GONE
                }
                
                // 显示置顶指示器和免打扰图标
                binding.root.findViewById<android.view.View>(com.tongxun.R.id.viewTopIndicator)?.visibility = 
                    if (conversation.isTop) android.view.View.VISIBLE else android.view.View.GONE
                ivMuted.visibility = if (conversation.isMuted) android.view.View.VISIBLE else android.view.View.GONE
                
                // 加载头像
                if (conversation.type == ConversationType.GROUP) {
                    // 群聊：生成九宫格头像
                    loadGroupAvatar(conversation)
                } else {
                    // 单聊：加载普通头像
                    // 🔥 关键修复：如果头像为空，尝试从用户信息获取
                    if (conversation.targetAvatar.isNullOrBlank()) {
                        // 头像为空，可能需要更新会话信息（异步处理，不阻塞UI）
                        android.util.Log.w("ConversationAdapter", "⚠️ 单聊会话头像为空，可能需要更新 - conversationId: ${conversation.conversationId}, targetId: ${conversation.targetId}")
                    }
                    loadAvatar(conversation.targetAvatar, ivAvatar)
                }
                
                root.setOnClickListener {
                    onItemClick(conversation)
                }
                
                root.setOnLongClickListener {
                    showConversationMenu(conversation)
                    true
                }
            }
        }
        
        private fun showConversationMenu(conversation: ConversationEntity) {
            val menuItems = mutableListOf<String>()
            menuItems.add(if (conversation.isTop) "取消置顶" else "置顶")
            menuItems.add(if (conversation.isMuted) "取消免打扰" else "免打扰")
            menuItems.add("删除会话")
            
            android.app.AlertDialog.Builder(binding.root.context)
                .setItems(menuItems.toTypedArray()) { _, which ->
                    when (menuItems[which]) {
                        "置顶", "取消置顶" -> onTopClick?.invoke(conversation)
                        "免打扰", "取消免打扰" -> onMutedClick?.invoke(conversation)
                        "删除会话" -> onDeleteClick?.invoke(conversation)
                    }
                }
                .show()
        }
        
        private fun formatTime(timestamp: Long): String {
            // 🔥 关键修复：处理无效时间戳（0 或负数会显示 1970/01/01）
            if (timestamp <= 0) {
                return "" // 如果没有有效时间戳，返回空字符串
            }
            
            val calendar = Calendar.getInstance()
            val messageCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
            
            // 验证时间戳是否合理（不能是未来时间，也不能太早）
            val currentTime = System.currentTimeMillis()
            val minValidTime = currentTime - (365L * 24 * 3600 * 1000) // 一年前
            if (timestamp > currentTime || timestamp < minValidTime) {
                return "" // 如果时间戳不合理，返回空字符串
            }
            
            // 今天
            if (calendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)) {
                // 今天，显示时间
                return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
            
            // 昨天
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            if (calendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)) {
                return "昨天"
            }
            
            // 本周内
            calendar.timeInMillis = System.currentTimeMillis()
            val daysDiff = (calendar.timeInMillis - timestamp) / (24 * 3600 * 1000)
            if (daysDiff < 7) {
                val weekDays = arrayOf("", "周日", "周一", "周二", "周三", "周四", "周五", "周六")
                return weekDays[messageCalendar.get(Calendar.DAY_OF_WEEK)]
            }
            
            // 今年内，显示月日
            if (calendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR)) {
                return SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
            }
            
            // 跨年，显示年月日
            return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
        }
        
        /**
         * 加载头像
         */
        private fun loadAvatar(avatarUrl: String?, imageView: ImageView) {
            val fullUrl = ImageUrlUtils.getFullImageUrl(avatarUrl)
            if (fullUrl == null) {
                imageView.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
                return
            }
            
            Glide.with(binding.root.context)
                .load(fullUrl)
                .placeholder(com.tongxun.R.drawable.ic_launcher_round)
                .error(com.tongxun.R.drawable.ic_launcher_round)
                .centerCrop()
                .into(imageView)
        }
        
        /**
         * 加载群组头像（九宫格）
         */
        private fun loadGroupAvatar(conversation: ConversationEntity) {
            val groupId = conversation.targetId
            
            // 先显示默认头像
            binding.ivAvatar.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
            
            // 异步获取群成员头像并生成九宫格
            scope.launch {
                try {
                    val memberAvatars = withContext(Dispatchers.IO) {
                        getGroupMemberAvatars(groupId)
                    }
                    
                    if (memberAvatars.isEmpty()) {
                        // 如果没有成员头像，保持默认头像
                        return@launch
                    }
                    
                    // 生成九宫格头像
                    GroupAvatarGenerator.generateGroupAvatar(
                        context = binding.root.context,
                        memberAvatars = memberAvatars,
                        size = 200
                    ) { bitmap ->
                        if (bitmap != null) {
                            binding.ivAvatar.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ConversationAdapter", "加载群组头像失败 - groupId: $groupId", e)
                }
            }
        }
        
        /**
         * 格式化消息预览文本
         */
        private fun formatMessagePreview(conversation: ConversationEntity): String {
            val lastMessage = conversation.lastMessage ?: return ""
            
            // 如果消息内容以特殊前缀开头，说明是特殊类型消息
            // 检查是否是图片消息（URL通常以http开头）
            if (lastMessage.startsWith("http://") || lastMessage.startsWith("https://")) {
                // 检查URL是否是图片格式
                val lowerMessage = lastMessage.lowercase()
                if (lowerMessage.contains(".jpg") || lowerMessage.contains(".jpeg") || 
                    lowerMessage.contains(".png") || lowerMessage.contains(".gif") ||
                    lowerMessage.contains(".webp") || lowerMessage.contains("image")) {
                    return "[图片]"
                }
                // 如果是其他URL，可能是文件
                return "[文件]"
            }
            
            // 检查是否已经包含类型标识
            if (lastMessage.startsWith("[") && lastMessage.contains("]")) {
                return lastMessage
            }
            
            // 检查是否是语音消息（可能包含语音标识）
            if (lastMessage.contains("[语音]") || lastMessage.contains("[VOICE]") || 
                lastMessage.contains("[语音消息]") || lastMessage.contains("voice")) {
                return "[语音]"
            }
            
            // 检查是否是文件消息
            if (lastMessage.contains("[文件]") || lastMessage.contains("[FILE]") ||
                lastMessage.contains(".apk") || lastMessage.contains(".pdf") ||
                lastMessage.contains(".doc") || lastMessage.contains(".zip")) {
                return if (lastMessage.startsWith("[文件]")) lastMessage else "[文件]"
            }
            
            // 默认返回原文本（文本消息）
            return lastMessage
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<ConversationEntity>() {
        override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem.conversationId == newItem.conversationId
        }
        
        override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem == newItem
        }
    }
}

