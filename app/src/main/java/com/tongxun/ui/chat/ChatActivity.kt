package com.tongxun.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tongxun.databinding.ActivityChatBinding
import com.tongxun.data.model.MessageType
import com.tongxun.data.local.entity.ConversationType
import com.tongxun.data.remote.WebSocketManager
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.utils.AccountKickedManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    
    @Inject
    lateinit var webSocketManager: WebSocketManager
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    @Inject
    lateinit var userRepository: com.tongxun.domain.repository.UserRepository
    
    private val conversationId: String by lazy {
        intent.getStringExtra("conversation_id") ?: ""
    }
    private val targetId: String by lazy {
        intent.getStringExtra("target_id") ?: ""
    }
    private val targetName: String by lazy {
        intent.getStringExtra("target_name") ?: ""
    }
    
    // 图片选择器（相册）
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.sendImageMessage(it)
        }
    }
    
    // 拍照
    private var cameraImageUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let {
                viewModel.sendImageMessage(it)
            }
        }
    }
    
    // 文件选择器（过滤掉图片类型）
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 检测文件类型，如果是图片则提示用户使用图片按钮
            val mimeType = contentResolver.getType(it)
            if (mimeType != null && mimeType.startsWith("image/")) {
                android.util.Log.d("ChatActivity", "检测到图片文件，提示用户使用图片按钮 - mimeType: $mimeType")
                Toast.makeText(this, "请使用图片按钮发送图片", Toast.LENGTH_SHORT).show()
            } else {
                android.util.Log.d("ChatActivity", "检测到非图片文件，作为文件消息发送 - mimeType: $mimeType")
                viewModel.sendFileMessage(it)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setupAccountKickedObserver()
        
        viewModel.initConversation(conversationId, targetId)
        
        // 进入聊天界面时标记已读
        viewModel.markAsRead()
    }
    
    /**
     * 监听账号被踢事件，确保在ChatActivity中也能收到并处理
     */
    private fun setupAccountKickedObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccountKickedManager.accountKickedEvent.collect { message ->
                    android.util.Log.e("ChatActivity", "收到账号被踢事件，强制跳转到登录页面 - message: $message")
                    
                    // 断开WebSocket连接
                    try {
                        webSocketManager.disconnect()
                        android.util.Log.d("ChatActivity", "WebSocket已断开")
                    } catch (e: Exception) {
                        android.util.Log.e("ChatActivity", "断开WebSocket失败", e)
                    }
                    
                    // 使用全局管理器处理跳转
                    AccountKickedManager.handleAccountKicked(this@ChatActivity, message)
                }
            }
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = targetName
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // 添加视频通话按钮（仅单聊显示）
        lifecycleScope.launch {
            viewModel.conversationType.collect { type ->
                if (type == ConversationType.SINGLE) {
                    // 在工具栏右侧添加视频通话按钮
                    binding.toolbar.menu.clear()
                    binding.toolbar.inflateMenu(com.tongxun.R.menu.menu_video_call)
                    binding.toolbar.setOnMenuItemClickListener { item ->
                        if (item.itemId == com.tongxun.R.id.action_video_call) {
                            startVideoCall()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    binding.toolbar.menu.clear()
                }
            }
        }
        
        // 设置菜单项点击监听器
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.tongxun.R.id.action_group_info -> {
                    // 打开群信息页面
                    val intent = Intent(this@ChatActivity, com.tongxun.ui.group.GroupInfoActivity::class.java)
                    intent.putExtra("group_id", conversationId)
                    startActivity(intent)
                    true
                }
                com.tongxun.R.id.action_video_call -> {
                    // 发起视频通话
                    startVideoCall()
                    true
                }
                else -> false
            }
        }
        
        // 监听会话类型变化，动态更新菜单
        lifecycleScope.launch {
            viewModel.conversationType.collect { type ->
                android.util.Log.d("ChatActivity", "会话类型更新 - type: $type")
                invalidateOptionsMenu() // 触发菜单重新创建
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // 菜单会在 onPrepareOptionsMenu 中动态更新
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val currentType = viewModel.conversationType.value
        android.util.Log.d("ChatActivity", "准备菜单 - conversationType: $currentType")
        if (currentType == ConversationType.GROUP) {
            menuInflater.inflate(com.tongxun.R.menu.menu_group_chat, menu)
            android.util.Log.d("ChatActivity", "已添加群信息菜单项")
        }
        else if (currentType == ConversationType.SINGLE) {
            menuInflater.inflate(com.tongxun.R.menu.menu_video_call, menu)
            android.util.Log.d("ChatActivity", "已添加视频通话菜单项")
        }
        return super.onPrepareOptionsMenu(menu)
    }
    
    private var lastMessageId: String? = null
    private var isUserScrolling = false
    
    private fun setupRecyclerView() {
        val currentUserId = viewModel.getCurrentUserId()
        
        // 先创建适配器，默认不是群聊（稍后会根据会话类型更新）
        messageAdapter = MessageAdapter(
            currentUserId = currentUserId,
            isGroupChat = false, // 初始值，稍后根据会话类型更新
            getUserInfo = { userId, callback ->
                android.util.Log.e("ChatActivity", "🔥🔥🔥 getUserInfo 回调被调用 - userId: ${userId.take(8)}...")
                // 异步获取用户信息
                lifecycleScope.launch {
                    try {
                        android.util.Log.d("ChatActivity", "开始获取用户信息 - userId: ${userId.take(8)}...")
                        val user = viewModel.getUserInfo(userId)
                        android.util.Log.e("ChatActivity", "✅✅✅ 获取用户信息完成 - userId: ${userId.take(8)}..., user: ${if (user != null) "存在 (nickname: ${user.nickname}, avatar: ${user.avatar?.take(20)}...)" else "null"}")
                        callback(user)
                    } catch (e: Exception) {
                        android.util.Log.e("ChatActivity", "❌❌❌ 获取用户信息异常 - userId: ${userId.take(8)}...", e)
                        callback(null)
                    }
                }
            },
            onMessageLongClick = { message ->
                showMessageMenu(message)
            },
            onImageClick = { imageUrl ->
                viewImagePreview(imageUrl)
            },
            onReadStatsClick = { messageId ->
                viewModel.showReadStats(messageId)
            },
            onVoiceClick = { message ->
                playVoiceMessage(message)
            },
            onFileClick = { message ->
                // 点击文件消息，下载并打开文件
                viewModel.downloadFile(message)
            }
        )
        val layoutManager = LinearLayoutManager(this@ChatActivity).apply {
            stackFromEnd = true
        }
        binding.recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = messageAdapter
            
            // 上拉加载更多
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    // 检测用户是否在滚动
                    isUserScrolling = true
                    recyclerView.postDelayed({
                        isUserScrolling = false
                    }, 500)
                    
                    // 如果向上滚动且到达顶部，加载更多
                    if (dy < 0 && !recyclerView.canScrollVertically(-1)) {
                        viewModel.loadMoreMessages()
                    }
                }
            })
        }
    }
    
    /**
     * 滚动到底部（平滑滚动）
     */
    private fun scrollToBottom() {
        val itemCount = messageAdapter.itemCount
        if (itemCount > 0) {
            binding.recyclerView.post {
                binding.recyclerView.smoothScrollToPosition(itemCount - 1)
            }
        }
    }
    
    /**
     * 检查是否在底部（用户是否正在查看最新消息）
     */
    private fun isAtBottom(): Boolean {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
        if (layoutManager == null || messageAdapter.itemCount == 0) {
            return true
        }
        val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
        val totalItemCount = messageAdapter.itemCount
        // 如果最后可见位置是最后一条消息或接近最后一条（允许1-2条消息的误差）
        return lastVisiblePosition >= totalItemCount - 3
    }
    
    private fun showMessageMenu(message: com.tongxun.data.local.entity.MessageEntity) {
        val menuItems = mutableListOf<String>()
        
        when (message.messageType) {
            MessageType.TEXT -> {
                menuItems.add("复制")
            }
            MessageType.IMAGE -> {
                menuItems.add("查看大图")
            }
            MessageType.FILE -> {
                menuItems.add("下载")
            }
            else -> {}
        }
        
        // 检查是否可以撤回（2分钟内且是自己发送的）
        val messageAge = System.currentTimeMillis() - message.timestamp
        val canRecall = messageAge < 2 * 60 * 1000 && !message.isRecalled
        if (canRecall) {
            menuItems.add("撤回")
        }
        
        menuItems.add("删除")
        
        android.app.AlertDialog.Builder(this)
            .setItems(menuItems.toTypedArray()) { _, which ->
                when (menuItems[which]) {
                    "复制" -> {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("消息", message.content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    "查看大图" -> {
                        val extra = message.extra?.let { org.json.JSONObject(it) }
                        val imageUrl = extra?.optString("fileUrl") ?: message.content
                        viewImagePreview(imageUrl)
                    }
                    "下载" -> {
                        viewModel.downloadFile(message)
                    }
                    "撤回" -> {
                        viewModel.recallMessage(message.messageId)
                    }
                    "删除" -> {
                        viewModel.deleteMessage(message.messageId)
                    }
                }
            }
            .show()
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                android.util.Log.e("ChatActivity", "🔥🔥🔥 收到消息列表更新 - 共 ${messages.size} 条消息")
                if (messages.isNotEmpty()) {
                    messages.take(3).forEach { msg ->
                        android.util.Log.d("ChatActivity", "  消息: messageId=${msg.messageId.take(8)}..., senderId=${msg.senderId.take(8)}..., content=${msg.content.take(30)}...")
                    }
                } else {
                    android.util.Log.w("ChatActivity", "⚠️ 消息列表为空")
                }
                
                val previousSize = messageAdapter.itemCount
                val previousLastMessageId = lastMessageId
                
                messageAdapter.submitList(messages) {
                    if (messages.isEmpty()) {
                        lastMessageId = null
                        return@submitList
                    }
                    
                    val currentLastMessageId = messages.last().messageId
                    val hasNewMessage = previousLastMessageId != null && currentLastMessageId != previousLastMessageId
                    
                    // 如果加载了更多历史消息（向上加载），保持滚动位置
                    if (previousSize > 0 && messages.size > previousSize && !hasNewMessage) {
                        val newItemsCount = messages.size - previousSize
                        binding.recyclerView.scrollToPosition(newItemsCount)
                        android.util.Log.d("ChatActivity", "加载更多历史消息，保持滚动位置 - 位置: $newItemsCount")
                        lastMessageId = currentLastMessageId
                    } else if (messages.isNotEmpty() && previousSize == 0) {
                        // 首次加载，滚动到底部
                        android.util.Log.d("ChatActivity", "首次加载消息，滚动到底部 - 位置: ${messages.size - 1}")
                        binding.recyclerView.post {
                            binding.recyclerView.smoothScrollToPosition(messages.size - 1)
                        }
                        lastMessageId = currentLastMessageId
                    } else if (hasNewMessage) {
                        // 有新消息（发送或接收），自动滚动到底部
                        val wasAtBottom = isAtBottom()
                        android.util.Log.d("ChatActivity", "检测到新消息 - messageId: $currentLastMessageId, 之前在底部: $wasAtBottom")
                        
                        // 如果用户在底部附近（允许3条消息的误差），自动滚动到底部
                        // 或者用户没有主动滚动，也自动滚动到底部
                        if (wasAtBottom || !isUserScrolling) {
                            scrollToBottom()
                            android.util.Log.d("ChatActivity", "自动滚动到底部 - 位置: ${messages.size - 1}")
                        } else {
                            android.util.Log.d("ChatActivity", "用户正在查看历史消息，不自动滚动")
                        }
                        lastMessageId = currentLastMessageId
                    } else {
                        // 消息列表更新但没有新消息（可能是状态更新等）
                        lastMessageId = currentLastMessageId
                    }
                }
                
                // 检查是否还有更多消息
                if (messages.isNotEmpty()) {
                    viewModel.checkHasMoreMessages()
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoadingMore.collect { isLoading ->
                // 可以显示加载更多的指示器
                if (isLoading) {
                    // 显示加载指示器
                }
            }
        }
        
        // 🔥 观察会话类型，更新 MessageAdapter 的群聊标志
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.conversationType.collect { conversationType ->
                    conversationType?.let { type ->
                        val isGroupChat = type == com.tongxun.data.local.entity.ConversationType.GROUP
                        messageAdapter.updateGroupChatFlag(isGroupChat)
                        android.util.Log.d("ChatActivity", "会话类型已更新 - isGroupChat: $isGroupChat")
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                binding.btnSend.isEnabled = !state.isLoading && state.inputText.isNotBlank()
                
                state.error?.let { error ->
                    Toast.makeText(this@ChatActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
                
                state.uploadProgress?.let {
                    // 显示上传进度（可以添加进度条）
                }
                
                state.downloadedFile?.let { file ->
                    android.util.Log.d("ChatActivity", "文件下载完成: ${file.absolutePath}, fileName: ${file.name}, size: ${file.length()} bytes")
                    // 文件下载完成，检查是否是语音文件
                    val mimeType = getMimeType(file.name)
                    android.util.Log.d("ChatActivity", "文件 MIME 类型: $mimeType, pendingVoiceMessage: ${pendingVoiceMessage != null}")
                    
                    if (mimeType.startsWith("audio/") && pendingVoiceMessage != null) {
                        // 播放语音文件
                        val message = pendingVoiceMessage!!
                        android.util.Log.d("ChatActivity", "开始播放下载的语音文件: ${file.absolutePath}, messageId: ${message.messageId.take(8)}...")
                        
                        // 验证文件是否有效
                        if (!file.exists() || file.length() == 0L) {
                            android.util.Log.e("ChatActivity", "下载的文件无效: exists=${file.exists()}, size=${file.length()}")
                            Toast.makeText(this@ChatActivity, "语音文件下载失败", Toast.LENGTH_SHORT).show()
                            currentPlayingMessageId = null
                            pendingVoiceMessage = null
                            messageAdapter.updatePlayingState(message.messageId, false)
                        } else {
                            currentPlayingMessageId = message.messageId
                            messageAdapter.updatePlayingState(message.messageId, true)
                            audioPlayer.play(file) {
                                // 播放完成回调
                                android.util.Log.d("ChatActivity", "下载的语音播放完成: ${message.messageId.take(8)}...")
                                // 播放完成后清空状态，以便可以重新播放
                                val completedMessageId = currentPlayingMessageId
                                currentPlayingMessageId = null
                                if (completedMessageId != null) {
                                    messageAdapter.updatePlayingState(completedMessageId, false)
                                }
                            }
                            pendingVoiceMessage = null
                        }
                    } else {
                        // 打开其他文件
                        android.util.Log.d("ChatActivity", "打开非语音文件: ${file.name}")
                        openFile(file)
                    }
                    viewModel.clearDownloadedFile()
                }
                
                state.readStats?.let { stats ->
                    showReadStatsDialog(stats)
                    viewModel.clearReadStats()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            if (content.isNotBlank()) {
                viewModel.sendMessage(content)
                binding.etMessage.text?.clear()
            }
        }
        
        // 图片按钮 - 显示选择菜单（拍照/相册）
        binding.btnImage.setOnClickListener {
            if (checkPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                showImagePickerDialog()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        
        // 文件按钮
        binding.btnFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }
        
        // 表情按钮
        binding.btnEmoji.setOnClickListener {
            showEmojiPicker()
        }
        
        // 语音按钮
        binding.btnVoice.setOnClickListener {
            if (checkPermission(Manifest.permission.RECORD_AUDIO)) {
                showVoiceRecordDialog()
            } else {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        
        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateInputText(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
    
    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImagePickerDialog()
        } else {
            Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePicture()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示图片选择对话框（拍照/相册）
     */
    private fun showImagePickerDialog() {
        android.app.AlertDialog.Builder(this)
            .setItems(arrayOf("拍照", "从相册选择")) { _, which ->
                when (which) {
                    0 -> {
                        // 拍照
                        if (checkPermission(Manifest.permission.CAMERA)) {
                            takePicture()
                        } else {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> {
                        // 从相册选择
                        imagePickerLauncher.launch("image/*")
                    }
                }
            }
            .show()
    }
    
    /**
     * 拍照
     */
    private fun takePicture() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            
            cameraImageUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile
            )
            
            cameraLauncher.launch(cameraImageUri)
        } catch (e: Exception) {
            Toast.makeText(this, "无法创建图片文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceRecordDialog()
        } else {
            Toast.makeText(this, "需要录音权限才能录制语音", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun viewImagePreview(imageUrl: String) {
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putExtra("image_url", imageUrl)
        }
        startActivity(intent)
    }
    
    private fun openFile(file: java.io.File) {
        try {
            android.util.Log.d("ChatActivity", "准备打开文件: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
            
            if (!file.exists()) {
                android.util.Log.e("ChatActivity", "文件不存在: ${file.absolutePath}")
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (file.length() == 0L) {
                android.util.Log.e("ChatActivity", "文件大小为0: ${file.absolutePath}")
                Toast.makeText(this, "文件为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            val mimeType = getMimeType(file.name)
            android.util.Log.d("ChatActivity", "文件MIME类型: $mimeType")
            
            // 尝试使用 FileProvider，如果失败则使用 MediaStore
            val uri: Uri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("ChatActivity", "FileProvider 失败，尝试使用 MediaStore: ${e.message}", e)
                // 如果 FileProvider 失败，使用 MediaStore 将文件复制到公共媒体库
                try {
                    if (mimeType.startsWith("video/")) {
                        // 对于视频文件，使用 MediaStore.Video（必须使用 Movies 目录）
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
                            put(android.provider.MediaStore.Video.Media.MIME_TYPE, mimeType)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                            }
                        }
                        val mediaUri = contentResolver.insert(
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            values
                        ) ?: throw Exception("无法创建 MediaStore 条目")
                        
                        // 复制文件内容到 MediaStore
                        contentResolver.openOutputStream(mediaUri)?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        android.util.Log.d("ChatActivity", "文件已复制到 MediaStore: $mediaUri")
                        mediaUri
                    } else {
                        // 对于其他文件类型，使用 MediaStore.Downloads (Android 10+)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
                                put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
                                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }
                            val downloadUri = contentResolver.insert(
                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                values
                            ) ?: throw Exception("无法创建 MediaStore 条目")
                            
                            // 复制文件内容到 MediaStore
                            contentResolver.openOutputStream(downloadUri)?.use { output ->
                                file.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            android.util.Log.d("ChatActivity", "文件已复制到 MediaStore: $downloadUri")
                            downloadUri
                        } else {
                            // Android 9 及以下，提示用户手动打开文件
                            throw Exception("请手动在文件管理器中打开文件：${file.absolutePath}")
                        }
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("ChatActivity", "MediaStore 也失败: ${e2.message}", e2)
                    Toast.makeText(this, "无法打开文件：${e2.message}\n文件位置：${file.absolutePath}", Toast.LENGTH_LONG).show()
                    return
                }
            }
            
            android.util.Log.d("ChatActivity", "文件 URI: $uri")
            
            // 应用内打开：根据文件类型选择不同的处理方式
            when {
                // 图片文件：在应用内查看
                mimeType.startsWith("image/") -> {
                    viewImagePreview(file.absolutePath)
                }
                // 视频文件：在应用内播放（如果有视频播放器）
                mimeType.startsWith("video/") -> {
                    // 使用系统视频播放器（暂时，后续可以集成应用内播放器）
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "无法播放视频", Toast.LENGTH_SHORT).show()
                    }
                }
                // 音频文件：在应用内播放（语音消息已经在应用内播放）
                mimeType.startsWith("audio/") -> {
                    // 语音消息已经在应用内播放，其他音频文件也使用系统播放器
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "无法播放音频", Toast.LENGTH_SHORT).show()
                    }
                }
                // 文本文件：显示文件内容
                mimeType.startsWith("text/") || file.extension.lowercase() in listOf("txt", "log", "json", "xml", "html", "css", "js") -> {
                    // 显示文件内容对话框
                    showTextFileContent(file)
                }
                // PDF文件：使用系统PDF查看器（暂时）
                mimeType == "application/pdf" -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "无法打开PDF文件", Toast.LENGTH_SHORT).show()
                    }
                }
                // 其他文件：显示文件信息
                else -> {
                    showFileInfo(file)
                }
            }
        } catch (e: android.content.ActivityNotFoundException) {
            android.util.Log.e("ChatActivity", "ActivityNotFoundException: ${e.message}", e)
            Toast.makeText(this, "没有找到可以打开该文件的应用", Toast.LENGTH_SHORT).show()
        } catch (e: java.lang.IllegalArgumentException) {
            android.util.Log.e("ChatActivity", "IllegalArgumentException (可能是FileProvider路径问题): ${e.message}", e)
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "打开文件失败: ${e.message}", e)
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "amr" -> "audio/amr"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }
    
    private fun showTextFileContent(file: java.io.File) {
        try {
            val content = file.readText()
            val dialogView = android.view.LayoutInflater.from(this)
                .inflate(android.R.layout.simple_list_item_1, null)
            val textView = dialogView.findViewById<android.widget.TextView>(android.R.id.text1)
            textView.text = content
            textView.textSize = 12f
            textView.setPadding(32, 32, 32, 32)
            
            android.app.AlertDialog.Builder(this)
                .setTitle(file.name)
                .setView(textView)
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "读取文本文件失败", e)
            Toast.makeText(this, "无法读取文件内容: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showFileInfo(file: java.io.File) {
        val size = file.length()
        val sizeText = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
        
        val message = "文件名: ${file.name}\n" +
                "大小: $sizeText\n" +
                "路径: ${file.absolutePath}\n" +
                "类型: ${getMimeType(file.name)}"
        
        android.app.AlertDialog.Builder(this)
            .setTitle("文件信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showReadStatsDialog(stats: com.tongxun.data.remote.dto.MessageReadStatsDto) {
        val message = "已读 ${stats.readCount}/${stats.totalCount} 人"
        val readerNames = stats.readers.joinToString(", ") { it.nickname }
        val fullMessage = if (readerNames.isNotEmpty()) {
            "$message\n\n已读用户：$readerNames"
        } else {
            message
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("已读统计")
            .setMessage(fullMessage)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showEmojiPicker() {
        val dialog = EmojiPickerDialog()
        dialog.setOnEmojiSelectedListener { emoji ->
            val currentText = binding.etMessage.text.toString()
            val newText = currentText + emoji
            binding.etMessage.setText(newText)
            binding.etMessage.setSelection(newText.length)
        }
        dialog.show(supportFragmentManager, "EmojiPickerDialog")
    }
    
    private fun showVoiceRecordDialog() {
        android.util.Log.e("ChatActivity", "🔥🔥🔥 showVoiceRecordDialog() 被调用")
        val dialog = VoiceRecordDialog()
        android.util.Log.d("ChatActivity", "创建 VoiceRecordDialog，设置 listener")
        android.util.Log.d("ChatActivity", "当前 conversationId: '$conversationId', targetId: '$targetId'")
        val listener: (File, Int) -> Unit = { file, duration ->
            android.util.Log.e("ChatActivity", "🔥🔥🔥🔥🔥 收到录音完成回调 - file: ${file.absolutePath}, duration: $duration")
            android.util.Log.d("ChatActivity", "准备调用 viewModel.sendVoiceMessage()")
            try {
                android.util.Log.d("ChatActivity", "viewModel 是否为 null: ${viewModel == null}")
                viewModel.sendVoiceMessage(file, duration)
                android.util.Log.d("ChatActivity", "viewModel.sendVoiceMessage() 调用完成")
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "❌❌❌ 调用 sendVoiceMessage 时发生异常", e)
            }
        }
        android.util.Log.d("ChatActivity", "设置 listener 到 dialog")
        dialog.setOnRecordCompleteListener(listener)
        android.util.Log.d("ChatActivity", "显示 VoiceRecordDialog")
        try {
            dialog.show(supportFragmentManager, "VoiceRecordDialog")
            android.util.Log.d("ChatActivity", "VoiceRecordDialog 已显示")
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "显示 VoiceRecordDialog 失败", e)
            Toast.makeText(this, "无法打开录音功能: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val audioPlayer = com.tongxun.utils.AudioPlayer()
    private var pendingVoiceMessage: com.tongxun.data.local.entity.MessageEntity? = null
    private var currentPlayingMessageId: String? = null
    
    /**
     * 发起视频通话
     */
    private fun startVideoCall() {
        // 检查权限
        if (checkPermission(Manifest.permission.CAMERA) && checkPermission(Manifest.permission.RECORD_AUDIO)) {
            val intent = Intent(this, com.tongxun.ui.video.VideoCallActivity::class.java)
            intent.putExtra("target_user_id", targetId)
            intent.putExtra("target_user_name", targetName)
            intent.putExtra("is_incoming", false)
            startActivity(intent)
        } else {
            // 请求权限
            if (!checkPermission(Manifest.permission.CAMERA)) {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun playVoiceMessage(message: com.tongxun.data.local.entity.MessageEntity) {
        val extra = message.extra?.let { org.json.JSONObject(it) } ?: return
        val fileId = extra.optString("fileId")
        if (fileId.isBlank()) {
            android.util.Log.w("ChatActivity", "播放语音消息失败 - fileId 为空")
            return
        }
        val fileName = extra.optString("fileName", "voice.m4a")
        
        // 如果正在播放同一个消息，则暂停
        if (currentPlayingMessageId == message.messageId && audioPlayer.isPlaying()) {
            android.util.Log.d("ChatActivity", "正在播放同一个消息，暂停播放")
            audioPlayer.pause()
            messageAdapter.updatePlayingState(message.messageId, false)
            return
        }
        
        // 如果暂停状态，恢复播放
        val playbackState = audioPlayer.playbackState.value
        if (currentPlayingMessageId == message.messageId && !audioPlayer.isPlaying() && playbackState is com.tongxun.utils.AudioPlayer.PlaybackState.Paused) {
            android.util.Log.d("ChatActivity", "恢复播放已暂停的消息")
            audioPlayer.resume()
            messageAdapter.updatePlayingState(message.messageId, true)
            return
        }
        
        // 如果播放已完成或已停止，允许重新播放（清空状态，继续执行播放逻辑）
        if (currentPlayingMessageId == message.messageId && !audioPlayer.isPlaying()) {
            android.util.Log.d("ChatActivity", "播放已完成或已停止，允许重新播放")
            currentPlayingMessageId = null
            messageAdapter.updatePlayingState(message.messageId, false)
            // 继续执行下面的播放逻辑
        }
        
        // 停止当前播放（如果正在播放其他消息）
        if (currentPlayingMessageId != null && currentPlayingMessageId != message.messageId) {
            android.util.Log.d("ChatActivity", "停止当前播放的消息，切换到新消息")
            audioPlayer.stop()
            val previousMessageId = currentPlayingMessageId
            currentPlayingMessageId = null
            if (previousMessageId != null) {
                messageAdapter.updatePlayingState(previousMessageId, false)
            }
        }
        
        // 判断是发送方还是接收方
        val currentUserId = viewModel.getCurrentUserId()
        val isSender = message.senderId == currentUserId
        
        android.util.Log.d("ChatActivity", "播放语音消息 - messageId: ${message.messageId.take(8)}..., isSender: $isSender, fileName: $fileName")
        
        // 统一的播放函数
        fun playLocalFile(file: java.io.File, source: String) {
            if (!file.exists()) {
                android.util.Log.e("ChatActivity", "[$source] 文件不存在: ${file.absolutePath}")
                return
            }
            
            android.util.Log.d("ChatActivity", "[$source] 开始播放本地文件: ${file.absolutePath}")
            currentPlayingMessageId = message.messageId
            messageAdapter.updatePlayingState(message.messageId, true)
            
            audioPlayer.play(file) {
                // 播放完成回调
                android.util.Log.d("ChatActivity", "[$source] 语音播放完成: ${message.messageId.take(8)}...")
                // 播放完成后，更新UI状态
                messageAdapter.updatePlayingState(message.messageId, false)
                // 清空 currentPlayingMessageId，以便再次点击时能够重新播放
                if (currentPlayingMessageId == message.messageId) {
                    currentPlayingMessageId = null
                }
            }
        }
        
        if (isSender) {
            // 发送方：先本地再服务器
            // 1. 优先检查本地文件路径（刚发送的语音消息）
            val localFilePath = extra.optString("localFilePath")
            val localFile = if (localFilePath.isNotEmpty()) {
                val file = java.io.File(localFilePath)
                if (file.exists()) {
                    android.util.Log.d("ChatActivity", "[发送方] 使用本地文件路径: $localFilePath")
                    file
                } else {
                    android.util.Log.w("ChatActivity", "[发送方] 本地文件路径不存在: $localFilePath，尝试已下载的文件")
                    null
                }
            } else {
                null
            } ?: com.tongxun.utils.FileManager.getLocalFile(this, fileName, false)
            
            if (localFile != null && localFile.exists()) {
                playLocalFile(localFile, "发送方")
            } else {
                // 本地文件不存在，从服务器下载
                android.util.Log.d("ChatActivity", "[发送方] 本地文件不存在，开始从服务器下载: fileName=$fileName")
                pendingVoiceMessage = message
                currentPlayingMessageId = message.messageId
                messageAdapter.updatePlayingState(message.messageId, true)
                viewModel.downloadFile(message)
            }
        } else {
            // 接收方：先服务器再本地
            // 1. 先检查是否已下载到本地
            val localFile = com.tongxun.utils.FileManager.getLocalFile(this, fileName, false)
            
            if (localFile != null && localFile.exists()) {
                playLocalFile(localFile, "接收方")
            } else {
                // 本地文件不存在，从服务器下载
                android.util.Log.d("ChatActivity", "[接收方] 本地文件不存在，开始从服务器下载: fileName=$fileName")
                pendingVoiceMessage = message
                currentPlayingMessageId = message.messageId
                messageAdapter.updatePlayingState(message.messageId, true)
                viewModel.downloadFile(message)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 停止语音播放
        audioPlayer.stop()
        viewModel.clearError()
    }
    
    override fun onPause() {
        super.onPause()
        // 暂停语音播放（可选，根据需求决定是否暂停）
        // audioPlayer.pause()
    }
}
