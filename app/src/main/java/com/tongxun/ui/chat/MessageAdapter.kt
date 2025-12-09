package com.tongxun.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tongxun.data.local.entity.MessageEntity
import com.tongxun.data.local.entity.UserEntity
import com.tongxun.data.model.MessageType
import com.tongxun.databinding.ItemMessageReceivedBinding
import com.tongxun.databinding.ItemMessageSentBinding
import com.tongxun.databinding.ItemMessageImageSentBinding
import com.tongxun.databinding.ItemMessageImageReceivedBinding
import com.tongxun.databinding.ItemMessageFileSentBinding
import com.tongxun.databinding.ItemMessageFileReceivedBinding
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val currentUserId: String,
    private var isGroupChat: Boolean = false,
    private val getUserInfo: ((String, (UserEntity?) -> Unit) -> Unit)? = null,
    private val onMessageLongClick: ((MessageEntity) -> Unit)? = null,
    private val onImageClick: ((String) -> Unit)? = null,
    private val onReadStatsClick: ((String) -> Unit)? = null,
    private val onVoiceClick: ((MessageEntity) -> Unit)? = null,
    private val onFileClick: ((MessageEntity) -> Unit)? = null
) : ListAdapter<MessageEntity, RecyclerView.ViewHolder>(DiffCallback()) {
    
    // 缓存用户信息，避免重复获取
    private val userInfoCache = mutableMapOf<String, UserEntity?>()
    
    init {
        android.util.Log.e("MessageAdapter", "🔥🔥🔥 MessageAdapter 初始化 - currentUserId: ${currentUserId.take(8)}..., isGroupChat: $isGroupChat, getUserInfo: ${if (getUserInfo != null) "已设置" else "null"}")
    }
    
    /**
     * 更新群聊标志
     */
    fun updateGroupChatFlag(isGroup: Boolean) {
        if (isGroupChat != isGroup) {
            android.util.Log.d("MessageAdapter", "更新群聊标志 - 从 $isGroupChat 变为 $isGroup")
            isGroupChat = isGroup
            // 通知所有接收消息项更新，以显示/隐藏发送者信息
            notifyDataSetChanged()
        }
    }
    
    /**
     * 清除用户信息缓存（重新登录后调用）
     */
    fun clearUserInfoCache() {
        android.util.Log.e("MessageAdapter", "🔥🔥🔥 清除用户信息缓存 - 当前缓存大小: ${userInfoCache.size}")
        userInfoCache.clear()
        android.util.Log.d("MessageAdapter", "✅ 用户信息缓存已清除")
    }
    
    /**
     * 预加载用户信息到缓存
     */
    fun preloadUserInfo(userId: String, user: UserEntity?) {
        if (user != null) {
            android.util.Log.d("MessageAdapter", "预加载用户信息到缓存 - userId: ${userId.take(8)}..., nickname: ${user.nickname}")
            userInfoCache[userId] = user
        }
    }
    
    // 记录正在播放的语音消息ID
    private val playingMessageIds = mutableSetOf<String>()
    
    /**
     * 更新语音消息的播放状态
     */
    fun updatePlayingState(messageId: String, isPlaying: Boolean) {
        if (isPlaying) {
            playingMessageIds.add(messageId)
        } else {
            playingMessageIds.remove(messageId)
        }
        // 通知适配器更新相关项
        val position = currentList.indexOfFirst { it.messageId == messageId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }
    
    companion object {
        private const val TYPE_TEXT_SENT = 1
        private const val TYPE_TEXT_RECEIVED = 2
        private const val TYPE_IMAGE_SENT = 3
        private const val TYPE_IMAGE_RECEIVED = 4
        private const val TYPE_FILE_SENT = 5
        private const val TYPE_FILE_RECEIVED = 6
        private const val TYPE_VOICE_SENT = 7
        private const val TYPE_VOICE_RECEIVED = 8
    }
    
    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        val isSent = message.senderId == currentUserId
        
        return when (message.messageType) {
            MessageType.IMAGE -> if (isSent) TYPE_IMAGE_SENT else TYPE_IMAGE_RECEIVED
            MessageType.FILE -> if (isSent) TYPE_FILE_SENT else TYPE_FILE_RECEIVED
            MessageType.VOICE -> if (isSent) TYPE_VOICE_SENT else TYPE_VOICE_RECEIVED
            else -> if (isSent) TYPE_TEXT_SENT else TYPE_TEXT_RECEIVED
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TEXT_SENT -> {
                val binding = ItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentTextViewHolder(binding)
            }
            TYPE_TEXT_RECEIVED -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedTextViewHolder(binding)
            }
            TYPE_IMAGE_SENT -> {
                val binding = ItemMessageImageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentImageViewHolder(binding)
            }
            TYPE_IMAGE_RECEIVED -> {
                val binding = ItemMessageImageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedImageViewHolder(binding)
            }
            TYPE_FILE_SENT -> {
                val binding = ItemMessageFileSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentFileViewHolder(binding)
            }
            TYPE_FILE_RECEIVED -> {
                val binding = ItemMessageFileReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedFileViewHolder(binding)
            }
            TYPE_VOICE_SENT -> {
                val binding = com.tongxun.databinding.ItemMessageVoiceSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentVoiceViewHolder(binding)
            }
            TYPE_VOICE_RECEIVED -> {
                val binding = com.tongxun.databinding.ItemMessageVoiceReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedVoiceViewHolder(binding)
            }
            else -> {
                val binding = ItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentTextViewHolder(binding)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentTextViewHolder -> holder.bind(message)
            is ReceivedTextViewHolder -> holder.bind(message)
            is SentImageViewHolder -> holder.bind(message)
            is ReceivedImageViewHolder -> holder.bind(message)
            is SentFileViewHolder -> holder.bind(message)
            is ReceivedFileViewHolder -> holder.bind(message)
            is SentVoiceViewHolder -> holder.bind(message)
            is ReceivedVoiceViewHolder -> holder.bind(message)
        }
    }
    
    inner class SentTextViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            if (message.isRecalled) {
                binding.tvMessage.text = "您撤回了一条消息"
                binding.tvMessage.setTextColor(binding.root.context.getColor(android.R.color.darker_gray))
            } else {
                binding.tvMessage.text = message.content
                binding.tvMessage.setTextColor(binding.root.context.getColor(com.tongxun.R.color.text_primary))
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            val statusText = when (message.status) {
                com.tongxun.data.local.entity.MessageStatus.SENDING -> "发送中"
                com.tongxun.data.local.entity.MessageStatus.SENT -> "已发送"
                com.tongxun.data.local.entity.MessageStatus.READ -> "已读"
                com.tongxun.data.local.entity.MessageStatus.FAILED -> "失败"
                else -> ""
            }
            binding.tvStatus.text = statusText
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled && message.status != com.tongxun.data.local.entity.MessageStatus.SENDING) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
    }
    
    inner class ReceivedTextViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            android.util.Log.e("MessageAdapter", "🔥🔥🔥 ReceivedTextViewHolder.bind() - messageId: ${message.messageId.take(8)}..., senderId: ${message.senderId.take(8)}..., isGroupChat: $isGroupChat")
            
            // 🔥 单聊和群聊都显示发送者名称（单聊时隐藏头像，群聊时显示头像）
            if (isGroupChat) {
                android.util.Log.d("MessageAdapter", "群聊模式 - 显示头像和名称")
                // 群聊：显示头像和名称
                binding.ivSenderAvatar.visibility = android.view.View.VISIBLE
                binding.tvSenderName.visibility = android.view.View.VISIBLE
                
                // 从缓存获取或异步获取用户信息
                val cachedUser = userInfoCache[message.senderId]
                android.util.Log.d("MessageAdapter", "群聊 - senderId: ${message.senderId.take(8)}..., 缓存中用户: ${if (cachedUser != null) "存在 (${cachedUser.nickname})" else "不存在"}")
                
                if (cachedUser != null) {
                    android.util.Log.d("MessageAdapter", "✅ 群聊 - 使用缓存用户信息: ${cachedUser.nickname}")
                    displaySenderInfo(binding, cachedUser)
                } else {
                    // 先显示默认值
                    android.util.Log.w("MessageAdapter", "⚠️ 群聊 - 缓存中没有用户信息，显示默认值，开始异步获取")
                    binding.tvSenderName.text = "用户"
                    binding.ivSenderAvatar.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
                    
                    // 异步获取用户信息
                    if (getUserInfo != null) {
                        android.util.Log.d("MessageAdapter", "开始异步获取群聊用户信息 - senderId: ${message.senderId.take(8)}...")
                        getUserInfo.invoke(message.senderId) { user ->
                            android.util.Log.e("MessageAdapter", "🔥🔥🔥 群聊 - 收到用户信息回调 - senderId: ${message.senderId.take(8)}..., user: ${if (user != null) "存在 (${user.nickname})" else "null"}")
                            userInfoCache[message.senderId] = user
                            
                            // 验证 ViewHolder 是否还在显示这条消息（通过 messageId）
                            val position = bindingAdapterPosition
                            android.util.Log.d("MessageAdapter", "群聊 - 验证ViewHolder位置 - position: $position, listSize: ${currentList.size}, messageId: ${message.messageId.take(8)}...")
                            
                            if (position >= 0 && position < currentList.size) {
                                val currentMessage = getItem(position)
                                if (currentMessage?.messageId == message.messageId) {
                                    android.util.Log.e("MessageAdapter", "✅✅✅ 群聊 - ViewHolder验证通过，更新UI - nickname: ${user?.nickname ?: "用户"}")
                                    binding.root.post {
                                        displaySenderInfo(binding, user)
                                    }
                                } else {
                                    android.util.Log.w("MessageAdapter", "⚠️ 群聊 - ViewHolder验证失败，消息已变化 - currentMessageId: ${currentMessage?.messageId?.take(8)}..., expectedMessageId: ${message.messageId.take(8)}...")
                                }
                            } else {
                                android.util.Log.w("MessageAdapter", "⚠️ 群聊 - ViewHolder位置无效 - position: $position, listSize: ${currentList.size}")
                            }
                        }
                    } else {
                        android.util.Log.e("MessageAdapter", "❌❌❌ 群聊 - getUserInfo 回调为 null！无法获取用户信息")
                    }
                }
            } else {
                android.util.Log.d("MessageAdapter", "单聊模式 - 只显示名称，隐藏头像")
                // 单聊：只显示名称，隐藏头像
                binding.ivSenderAvatar.visibility = android.view.View.GONE
                binding.tvSenderName.visibility = android.view.View.VISIBLE
                
                // 从缓存获取或异步获取用户信息
                val cachedUser = userInfoCache[message.senderId]
                android.util.Log.d("MessageAdapter", "单聊 - senderId: ${message.senderId.take(8)}..., 缓存中用户: ${if (cachedUser != null) "存在 (${cachedUser.nickname})" else "不存在"}")
                
                if (cachedUser != null) {
                    android.util.Log.d("MessageAdapter", "✅ 单聊 - 使用缓存用户信息: ${cachedUser.nickname}")
                    binding.tvSenderName.text = cachedUser.nickname
                } else {
                    // 先显示默认值
                    android.util.Log.w("MessageAdapter", "⚠️ 单聊 - 缓存中没有用户信息，显示默认值，开始异步获取 - senderId: ${message.senderId.take(8)}...")
                    binding.tvSenderName.text = "用户"
                    
                    // 异步获取用户信息
                    if (getUserInfo != null) {
                        android.util.Log.e("MessageAdapter", "🔥🔥🔥 开始异步获取单聊用户信息 - senderId: ${message.senderId.take(8)}...")
                        getUserInfo.invoke(message.senderId) { user ->
                            android.util.Log.e("MessageAdapter", "🔥🔥🔥 单聊 - 收到用户信息回调 - senderId: ${message.senderId.take(8)}..., user: ${if (user != null) "存在 (${user.nickname}, avatar: ${user.avatar?.take(20)}...)" else "null"}")
                            
                            if (user != null) {
                                userInfoCache[message.senderId] = user
                            }
                            
                            // 验证 ViewHolder 是否还在显示这条消息（通过 messageId）
                            val position = bindingAdapterPosition
                            android.util.Log.d("MessageAdapter", "单聊 - 验证ViewHolder位置 - position: $position, listSize: ${currentList.size}, messageId: ${message.messageId.take(8)}...")
                            
                            if (position >= 0 && position < currentList.size) {
                                val currentMessage = getItem(position)
                                if (currentMessage?.messageId == message.messageId) {
                                    val nickname = user?.nickname ?: "用户"
                                    android.util.Log.e("MessageAdapter", "✅✅✅ 单聊 - ViewHolder验证通过，更新UI - nickname: $nickname")
                                    binding.root.post {
                                        binding.tvSenderName.text = nickname
                                        android.util.Log.d("MessageAdapter", "✅ 单聊 - UI已更新 - nickname: $nickname")
                                    }
                                } else {
                                    android.util.Log.w("MessageAdapter", "⚠️ 单聊 - ViewHolder验证失败，消息已变化 - currentMessageId: ${currentMessage?.messageId?.take(8)}..., expectedMessageId: ${message.messageId.take(8)}...")
                                }
                            } else {
                                android.util.Log.w("MessageAdapter", "⚠️ 单聊 - ViewHolder位置无效 - position: $position, listSize: ${currentList.size}")
                            }
                        }
                    } else {
                        android.util.Log.e("MessageAdapter", "❌❌❌ 单聊 - getUserInfo 回调为 null！无法获取用户信息")
                    }
                }
            }
            
            if (message.isRecalled) {
                binding.tvMessage.text = "对方撤回了一条消息"
                binding.tvMessage.setTextColor(binding.root.context.getColor(android.R.color.darker_gray))
            } else {
                binding.tvMessage.text = message.content
                binding.tvMessage.setTextColor(binding.root.context.getColor(com.tongxun.R.color.text_primary))
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
        
        private fun displaySenderInfo(binding: ItemMessageReceivedBinding, user: UserEntity?) {
            if (user != null) {
                binding.tvSenderName.text = user.nickname
                val fullAvatarUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(user.avatar)
                binding.ivSenderAvatar.load(fullAvatarUrl) {
                    placeholder(com.tongxun.R.drawable.ic_launcher_round)
                    error(com.tongxun.R.drawable.ic_launcher_round)
                }
            } else {
                binding.tvSenderName.text = "用户"
                binding.ivSenderAvatar.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
            }
        }
        
        /**
         * 获取单聊时对方用户信息（用于显示头像）
         */
        private fun getOtherUserInfo(message: MessageEntity, callback: (UserEntity?) -> Unit) {
            // 单聊时，对方用户ID是 senderId（如果是接收的消息）或 receiverId（如果是发送的消息）
            // 但这里显示的是接收的消息，所以对方是 senderId
            val otherUserId = message.senderId
            
            // 先检查缓存
            val cachedUser = userInfoCache[otherUserId]
            if (cachedUser != null) {
                callback(cachedUser)
                return
            }
            
            // 如果缓存中没有，通过 getUserInfo 获取
            getUserInfo?.invoke(otherUserId) { user ->
                if (user != null) {
                    userInfoCache[otherUserId] = user
                }
                callback(user)
            }
        }
    }
    
    inner class SentImageViewHolder(
        private val binding: ItemMessageImageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            if (message.isRecalled) {
                binding.tvMessage.text = "您撤回了一条消息"
                binding.ivImage.visibility = android.view.View.GONE
                binding.tvMessage.visibility = android.view.View.VISIBLE
            } else {
                binding.tvMessage.visibility = android.view.View.GONE
                binding.ivImage.visibility = android.view.View.VISIBLE
                
                val extra = message.extra?.let { org.json.JSONObject(it) }
                val imageUrl = extra?.optString("thumbnailUrl") ?: extra?.optString("fileUrl") ?: message.content
                // 转换为完整URL
                val fullImageUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(imageUrl) ?: imageUrl
                
                binding.ivImage.load(fullImageUrl) {
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_report_image)
                }
                
                binding.ivImage.setOnClickListener {
                    val previewImageUrl = extra?.optString("fileUrl") ?: message.content
                    val fullPreviewUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(previewImageUrl) ?: previewImageUrl
                    onImageClick?.invoke(fullPreviewUrl)
                }
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            val statusText = when (message.status) {
                com.tongxun.data.local.entity.MessageStatus.SENDING -> "发送中"
                com.tongxun.data.local.entity.MessageStatus.SENT -> "已发送"
                com.tongxun.data.local.entity.MessageStatus.READ -> "已读"
                com.tongxun.data.local.entity.MessageStatus.FAILED -> "失败"
                else -> ""
            }
            binding.tvStatus.text = statusText
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled && message.status != com.tongxun.data.local.entity.MessageStatus.SENDING) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
    }
    
    inner class ReceivedImageViewHolder(
        private val binding: ItemMessageImageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            // 🔥 群聊时显示发送者信息
            displaySenderInfoForReceivedMessage(
                binding.ivSenderAvatar,
                binding.tvSenderName,
                message.senderId,
                message.messageId
            )
            
            if (message.isRecalled) {
                binding.tvMessage.text = "对方撤回了一条消息"
                binding.ivImage.visibility = android.view.View.GONE
                binding.tvMessage.visibility = android.view.View.VISIBLE
            } else {
                binding.tvMessage.visibility = android.view.View.GONE
                binding.ivImage.visibility = android.view.View.VISIBLE
                
                val extra = message.extra?.let { org.json.JSONObject(it) }
                val imageUrl = extra?.optString("thumbnailUrl") ?: extra?.optString("fileUrl") ?: message.content
                // 转换为完整URL
                val fullImageUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(imageUrl) ?: imageUrl
                
                binding.ivImage.load(fullImageUrl) {
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_report_image)
                }
                
                binding.ivImage.setOnClickListener {
                    val previewImageUrl = extra?.optString("fileUrl") ?: message.content
                    val fullPreviewUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(previewImageUrl) ?: previewImageUrl
                    onImageClick?.invoke(fullPreviewUrl)
                }
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
    }
    
    inner class SentFileViewHolder(
        private val binding: ItemMessageFileSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            if (message.isRecalled) {
                binding.tvFileName.text = "您撤回了一条消息"
                binding.ivFileIcon.visibility = android.view.View.GONE
            } else {
                val extra = message.extra?.let { org.json.JSONObject(it) }
                val fileName = extra?.optString("fileName") ?: "文件"
                val fileSize = extra?.optLong("fileSize", 0) ?: 0
                
                binding.tvFileName.text = fileName
                binding.tvFileSize.text = com.tongxun.utils.ImageUtils.formatFileSize(fileSize)
                binding.ivFileIcon.visibility = android.view.View.VISIBLE
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            val statusText = when (message.status) {
                com.tongxun.data.local.entity.MessageStatus.SENDING -> "发送中"
                com.tongxun.data.local.entity.MessageStatus.SENT -> "已发送"
                com.tongxun.data.local.entity.MessageStatus.READ -> "已读"
                com.tongxun.data.local.entity.MessageStatus.FAILED -> "失败"
                else -> ""
            }
            binding.tvStatus.text = statusText
            
            // 点击下载并打开文件
            binding.root.setOnClickListener {
                if (!message.isRecalled && message.status != com.tongxun.data.local.entity.MessageStatus.SENDING) {
                    onFileClick?.invoke(message)
                }
            }
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled && message.status != com.tongxun.data.local.entity.MessageStatus.SENDING) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
    }
    
    inner class ReceivedFileViewHolder(
        private val binding: ItemMessageFileReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            // 🔥 群聊时显示发送者信息
            displaySenderInfoForReceivedMessage(
                binding.ivSenderAvatar,
                binding.tvSenderName,
                message.senderId,
                message.messageId
            )
            
            if (message.isRecalled) {
                binding.tvFileName.text = "对方撤回了一条消息"
                binding.ivFileIcon.visibility = android.view.View.GONE
            } else {
                val extra = message.extra?.let { org.json.JSONObject(it) }
                val fileName = extra?.optString("fileName") ?: "文件"
                val fileSize = extra?.optLong("fileSize", 0) ?: 0
                
                binding.tvFileName.text = fileName
                binding.tvFileSize.text = com.tongxun.utils.ImageUtils.formatFileSize(fileSize)
                binding.ivFileIcon.visibility = android.view.View.VISIBLE
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            // 点击下载并打开文件
            binding.root.setOnClickListener {
                if (!message.isRecalled) {
                    onFileClick?.invoke(message)
                }
            }
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
    }
    
    inner class SentVoiceViewHolder(
        private val binding: com.tongxun.databinding.ItemMessageVoiceSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            if (message.isRecalled) {
                binding.root.visibility = android.view.View.GONE
            } else {
                binding.root.visibility = android.view.View.VISIBLE
                
                val extra = message.extra?.let { org.json.JSONObject(it) }
                val duration = extra?.optInt("duration", 0) ?: 0
                
                binding.tvDuration.text = "${duration}\""
                
                // 更新波形视图的播放状态
                val isPlaying = playingMessageIds.contains(message.messageId)
                binding.waveformView.setPlaying(isPlaying)
                
                // 点击整个消息区域都可以播放
                binding.messageContainer.setOnClickListener {
                    onVoiceClick?.invoke(message)
                }
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            val statusText = when (message.status) {
                com.tongxun.data.local.entity.MessageStatus.SENDING -> "发送中"
                com.tongxun.data.local.entity.MessageStatus.SENT -> "已发送"
                com.tongxun.data.local.entity.MessageStatus.READ -> "已读"
                com.tongxun.data.local.entity.MessageStatus.FAILED -> "失败"
                else -> ""
            }
            binding.tvStatus.text = statusText
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled && message.status != com.tongxun.data.local.entity.MessageStatus.SENDING) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
    }
    
    inner class ReceivedVoiceViewHolder(
        private val binding: com.tongxun.databinding.ItemMessageVoiceReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: MessageEntity) {
            // 🔥 群聊时显示发送者信息
            displaySenderInfoForReceivedMessage(
                binding.ivSenderAvatar,
                binding.tvSenderName,
                message.senderId,
                message.messageId
            )
            
            if (message.isRecalled) {
                binding.root.visibility = android.view.View.GONE
            } else {
                binding.root.visibility = android.view.View.VISIBLE
                
                val extra = message.extra?.let { org.json.JSONObject(it) }
                val duration = extra?.optInt("duration", 0) ?: 0
                
                binding.tvDuration.text = "${duration}\""
                
                // 更新波形视图的播放状态
                val isPlaying = playingMessageIds.contains(message.messageId)
                binding.waveformView.setPlaying(isPlaying)
                
                // 点击整个消息容器都可以播放
                binding.messageContainer.setOnClickListener {
                    onVoiceClick?.invoke(message)
                }
            }
            
            binding.tvTime.text = formatTime(message.timestamp)
            
            binding.root.setOnLongClickListener {
                if (!message.isRecalled) {
                    onMessageLongClick?.invoke(message)
                }
                true
            }
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    
    /**
     * 通用的显示发送者信息的辅助方法（用于图片、文件、语音等接收消息）
     */
    private fun displaySenderInfoForReceivedMessage(
        avatarView: android.widget.ImageView?,
        nameView: android.widget.TextView?,
        userId: String,
        messageId: String
    ) {
        android.util.Log.d("MessageAdapter", "displaySenderInfoForReceivedMessage() - userId: ${userId.take(8)}..., messageId: ${messageId.take(8)}..., isGroupChat: $isGroupChat")
        
        if (nameView == null) {
            android.util.Log.w("MessageAdapter", "⚠️ nameView 为 null，无法显示用户信息")
            return
        }
        
        if (isGroupChat) {
            android.util.Log.d("MessageAdapter", "群聊模式（通用方法）- 显示头像和名称")
            // 群聊：显示头像和名称
            avatarView?.visibility = android.view.View.VISIBLE
            nameView.visibility = android.view.View.VISIBLE
            
            // 从缓存获取或异步获取用户信息
            val cachedUser = userInfoCache[userId]
            android.util.Log.d("MessageAdapter", "群聊（通用方法）- userId: ${userId.take(8)}..., 缓存中用户: ${if (cachedUser != null) "存在 (${cachedUser.nickname})" else "不存在"}")
            
            if (cachedUser != null) {
                android.util.Log.d("MessageAdapter", "✅ 群聊（通用方法）- 使用缓存用户信息: ${cachedUser.nickname}")
                nameView.text = cachedUser.nickname
                val fullAvatarUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(cachedUser.avatar)
                avatarView?.load(fullAvatarUrl) {
                    placeholder(com.tongxun.R.drawable.ic_launcher_round)
                    error(com.tongxun.R.drawable.ic_launcher_round)
                }
            } else {
                // 先显示默认值
                android.util.Log.w("MessageAdapter", "⚠️ 群聊（通用方法）- 缓存中没有用户信息，显示默认值，开始异步获取")
                nameView.text = "用户"
                avatarView?.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
                
                // 异步获取用户信息
                if (getUserInfo != null) {
                    android.util.Log.d("MessageAdapter", "开始异步获取群聊用户信息（通用方法）- userId: ${userId.take(8)}...")
                    getUserInfo.invoke(userId) { user ->
                        android.util.Log.e("MessageAdapter", "🔥🔥🔥 群聊（通用方法）- 收到用户信息回调 - userId: ${userId.take(8)}..., user: ${if (user != null) "存在 (${user.nickname})" else "null"}")
                        if (user != null) {
                            userInfoCache[userId] = user
                        }
                        
                        // 验证 ViewHolder 是否还在显示这条消息（通过 messageId）
                        val position = currentList.indexOfFirst { it.messageId == messageId }
                        android.util.Log.d("MessageAdapter", "群聊（通用方法）- 验证消息位置 - position: $position, messageId: ${messageId.take(8)}...")
                        
                        if (position >= 0) {
                            // 直接更新视图（因为我们已经有了 View 的引用）
                            android.util.Log.e("MessageAdapter", "✅✅✅ 群聊（通用方法）- 更新UI - nickname: ${user?.nickname ?: "用户"}")
                            avatarView?.post {
                                nameView.text = user?.nickname ?: "用户"
                                val fullAvatarUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(user?.avatar)
                                avatarView?.load(fullAvatarUrl) {
                                    placeholder(com.tongxun.R.drawable.ic_launcher_round)
                                    error(com.tongxun.R.drawable.ic_launcher_round)
                                }
                            }
                        } else {
                            android.util.Log.w("MessageAdapter", "⚠️ 群聊（通用方法）- 消息位置无效，无法更新UI")
                        }
                    }
                } else {
                    android.util.Log.e("MessageAdapter", "❌❌❌ 群聊（通用方法）- getUserInfo 回调为 null！无法获取用户信息")
                }
            }
        } else {
            android.util.Log.d("MessageAdapter", "单聊模式（通用方法）- 只显示名称，隐藏头像")
            // 单聊：只显示名称，隐藏头像
            avatarView?.visibility = android.view.View.GONE
            nameView.visibility = android.view.View.VISIBLE
            
            // 从缓存获取或异步获取用户信息
            val cachedUser = userInfoCache[userId]
            android.util.Log.d("MessageAdapter", "单聊（通用方法）- userId: ${userId.take(8)}..., 缓存中用户: ${if (cachedUser != null) "存在 (${cachedUser.nickname})" else "不存在"}")
            
            if (cachedUser != null) {
                android.util.Log.d("MessageAdapter", "✅ 单聊（通用方法）- 使用缓存用户信息: ${cachedUser.nickname}")
                nameView.text = cachedUser.nickname
            } else {
                // 先显示默认值
                android.util.Log.w("MessageAdapter", "⚠️ 单聊（通用方法）- 缓存中没有用户信息，显示默认值，开始异步获取 - userId: ${userId.take(8)}...")
                nameView.text = "用户"
                
                // 异步获取用户信息
                if (getUserInfo != null) {
                    android.util.Log.e("MessageAdapter", "🔥🔥🔥 开始异步获取单聊用户信息（通用方法）- userId: ${userId.take(8)}...")
                    getUserInfo.invoke(userId) { user ->
                        android.util.Log.e("MessageAdapter", "🔥🔥🔥 单聊（通用方法）- 收到用户信息回调 - userId: ${userId.take(8)}..., user: ${if (user != null) "存在 (${user.nickname}, avatar: ${user.avatar?.take(20)}...)" else "null"}")
                        
                        if (user != null) {
                            userInfoCache[userId] = user
                        }
                        
                        // 验证 ViewHolder 是否还在显示这条消息（通过 messageId）
                        val position = currentList.indexOfFirst { it.messageId == messageId }
                        android.util.Log.d("MessageAdapter", "单聊（通用方法）- 验证消息位置 - position: $position, messageId: ${messageId.take(8)}...")
                        
                        if (position >= 0) {
                            // 直接更新视图
                            val nickname = user?.nickname ?: "用户"
                            android.util.Log.e("MessageAdapter", "✅✅✅ 单聊（通用方法）- 更新UI - nickname: $nickname")
                            nameView.post {
                                nameView.text = nickname
                                android.util.Log.d("MessageAdapter", "✅ 单聊（通用方法）- UI已更新 - nickname: $nickname")
                            }
                        } else {
                            android.util.Log.w("MessageAdapter", "⚠️ 单聊（通用方法）- 消息位置无效，无法更新UI")
                        }
                    }
                } else {
                    android.util.Log.e("MessageAdapter", "❌❌❌ 单聊（通用方法）- getUserInfo 回调为 null！无法获取用户信息")
                }
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            return oldItem.messageId == newItem.messageId
        }
        
        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            return oldItem == newItem
        }
    }
}
