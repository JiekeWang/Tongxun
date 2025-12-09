package com.tongxun.ui.group

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tongxun.databinding.ActivityGroupInfoBinding
import com.tongxun.ui.chat.ChatActivity
import com.tongxun.ui.main.MainActivity
import com.tongxun.domain.repository.ConversationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GroupInfoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityGroupInfoBinding
    private val viewModel: GroupInfoViewModel by viewModels()
    private lateinit var memberAdapter: GroupMemberAdapter
    
    @Inject
    lateinit var conversationRepository: ConversationRepository
    
    private val groupId: String by lazy {
        intent.getStringExtra("group_id") ?: ""
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        if (groupId.isEmpty()) {
            Toast.makeText(this, "群组ID不能为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        viewModel.loadGroupInfo(groupId)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "群信息"
    }
    
    private fun setupRecyclerView() {
        memberAdapter = GroupMemberAdapter(
            currentUserId = viewModel.currentUserId ?: "",
            isOwner = false, // 会在观察者中更新
            isAdmin = false, // 会在观察者中更新
            onMemberClick = { member ->
                // TODO: 打开成员详情
            },
            onMemberLongClick = { member ->
                // 长按删除成员（仅群主/管理员，且群组未解散）
                if (!viewModel.isDisbanded.value && (viewModel.isOwner || viewModel.isAdmin)) {
                    showRemoveMemberDialog(member)
                }
            }
        )
        
        binding.recyclerViewMembers.apply {
            layoutManager = LinearLayoutManager(this@GroupInfoActivity)
            adapter = memberAdapter
        }
    }
    
    private fun setupObservers() {
        // 观察群组是否已解散
        lifecycleScope.launch {
            viewModel.isDisbanded.collect { isDisbanded ->
                android.util.Log.d("GroupInfoActivity", "群组解散状态更新 - isDisbanded: $isDisbanded")
                updateUIForDisbandedState(isDisbanded)
            }
        }
        
        // 观察群组信息
        lifecycleScope.launch {
            viewModel.groupInfo.collect { groupInfo ->
                groupInfo?.let {
                    binding.tvGroupName.text = it.groupName
                    binding.tvGroupDescription.text = it.description ?: "暂无描述"
                    binding.tvMemberCount.text = "群成员 (${it.memberCount})"
                    
                    // 更新适配器的权限状态（如果群组已解散，禁用所有操作）
                    val isDisbanded = viewModel.isDisbanded.value
                    memberAdapter.updatePermissions(
                        isOwner = if (isDisbanded) false else viewModel.isOwner,
                        isAdmin = if (isDisbanded) false else viewModel.isAdmin
                    )
                    
                    // 更新添加成员按钮的可见性（仅群主/管理员可见，且群组未解散）
                    binding.btnAddMember.visibility = if (!isDisbanded && (viewModel.isOwner || viewModel.isAdmin)) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                    
                    // 更新菜单（仅群主显示解散群组选项，且群组未解散）
                    updateMenu()
                }
            }
        }
        
        // 观察群成员列表
        lifecycleScope.launch {
            viewModel.members.collect { members ->
                memberAdapter.submitList(members)
            }
        }
        
        // 观察加载状态
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
            }
        }
        
        // 观察错误
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@GroupInfoActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
        
        // 观察添加成员结果
        lifecycleScope.launch {
            viewModel.addMembersResult.collect { result ->
                result?.let {
                    if (it.isSuccess) {
                        val addedCount = it.getOrNull() ?: 0
                        android.util.Log.d("GroupInfoActivity", "添加成员成功 - addedCount: $addedCount")
                        if (addedCount > 0) {
                            Toast.makeText(this@GroupInfoActivity, "成功添加 $addedCount 个成员", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@GroupInfoActivity, "所选用户都已是群成员", Toast.LENGTH_SHORT).show()
                        }
                        // 刷新群组信息，确保成员列表更新
                        viewModel.refreshGroupInfo(groupId)
                    } else {
                        val errorMessage = it.exceptionOrNull()?.message ?: "添加成员失败"
                        android.util.Log.e("GroupInfoActivity", "添加成员失败: $errorMessage")
                        Toast.makeText(this@GroupInfoActivity, "添加成员失败: $errorMessage", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.clearAddMembersResult()
                }
            }
        }
        
        // 观察删除成员结果
        lifecycleScope.launch {
            viewModel.removeMemberResult.collect { result ->
                result?.let {
                    if (it.isSuccess) {
                        Toast.makeText(this@GroupInfoActivity, "删除成员成功", Toast.LENGTH_SHORT).show()
                        // 刷新群组信息，确保成员列表更新，这样 getAvailableFriends() 会返回正确的列表
                        viewModel.refreshGroupInfo(groupId)
                    } else {
                        Toast.makeText(this@GroupInfoActivity, "删除成员失败: ${it.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.clearRemoveMemberResult()
                }
            }
        }
        
        // 观察解散群组结果
        lifecycleScope.launch {
            viewModel.disbandGroupResult.collect { result ->
                result?.let {
                    if (it.isSuccess) {
                        Toast.makeText(this@GroupInfoActivity, "群组已解散", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@GroupInfoActivity, "解散群组失败: ${it.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.clearDisbandGroupResult()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnAddMember.setOnClickListener {
            showAddMemberDialog()
        }
        
        binding.btnDeleteConversation.setOnClickListener {
            showDeleteConversationDialog()
        }
    }
    
    private fun updateUIForDisbandedState(isDisbanded: Boolean) {
        if (isDisbanded) {
            // 群组已解散：隐藏所有操作按钮，显示删除会话按钮
            binding.btnAddMember.visibility = android.view.View.GONE
            binding.btnDeleteConversation.visibility = android.view.View.VISIBLE
            binding.recyclerViewMembers.visibility = android.view.View.GONE
            binding.tvMemberCount.visibility = android.view.View.GONE
            updateMenu() // 隐藏解散群组菜单项
            
            // 如果群组信息为空，显示默认信息
            if (viewModel.groupInfo.value == null) {
                binding.tvGroupName.text = "群组已解散"
                binding.tvGroupDescription.text = "此群组已被解散，您可以删除此会话"
            }
        } else {
            // 群组未解散：正常显示
            binding.btnDeleteConversation.visibility = android.view.View.GONE
            binding.recyclerViewMembers.visibility = android.view.View.VISIBLE
            binding.tvMemberCount.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun showDeleteConversationDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除此会话吗？删除后将无法查看历史消息。")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    try {
                        conversationRepository.deleteConversation(groupId)
                        Toast.makeText(this@GroupInfoActivity, "会话已删除", Toast.LENGTH_SHORT).show()
                        
                        // 跳转到消息页面（MainActivity），并清除返回栈
                        val intent = Intent(this@GroupInfoActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        android.util.Log.e("GroupInfoActivity", "删除会话失败", e)
                        Toast.makeText(this@GroupInfoActivity, "删除会话失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showAddMemberDialog() {
        lifecycleScope.launch {
            val availableFriends = viewModel.getAvailableFriends()
            
            if (availableFriends.isEmpty()) {
                Toast.makeText(this@GroupInfoActivity, "没有可添加的好友", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // 使用 SelectableFriendAdapter 来选择好友
            val selectedFriends = mutableSetOf<String>()
            val dialogView = android.view.LayoutInflater.from(this@GroupInfoActivity)
                .inflate(com.tongxun.R.layout.dialog_select_friends, null)
            val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.tongxun.R.id.recyclerViewFriends)
            
            // 将 FriendEntity 转换为 SelectableFriend
            val selectableFriends = availableFriends.map { friend ->
                SelectableFriend(friend = friend, isSelected = false)
            }
            
            // 先创建适配器（使用 lateinit 或直接定义）
            lateinit var adapter: SelectableFriendAdapter
            adapter = SelectableFriendAdapter { friend ->
                val wasSelected = selectedFriends.contains(friend.friendId)
                if (wasSelected) {
                    selectedFriends.remove(friend.friendId)
                    android.util.Log.d("GroupInfoActivity", "取消选择好友: ${friend.friendId}, 当前选中: ${selectedFriends.size}")
                } else {
                    selectedFriends.add(friend.friendId)
                    android.util.Log.d("GroupInfoActivity", "选择好友: ${friend.friendId}, 当前选中: ${selectedFriends.size}")
                }
                // 更新选中状态
                val currentList = adapter.currentList.toMutableList()
                val index = currentList.indexOfFirst { selectableFriend: SelectableFriend -> 
                    selectableFriend.friend.friendId == friend.friendId 
                }
                if (index >= 0) {
                    currentList[index] = currentList[index].copy(isSelected = !wasSelected)
                    adapter.submitList(currentList)
                }
            }
            
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@GroupInfoActivity)
            recyclerView.adapter = adapter
            adapter.submitList(selectableFriends)
            
            val dialog = AlertDialog.Builder(this@GroupInfoActivity)
                .setTitle("添加成员")
                .setView(dialogView)
                .setPositiveButton("确定", null) // 先设置为 null，稍后手动处理
                .setNegativeButton("取消", null)
                .create()
            
            dialog.setOnShowListener {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                // 清除之前的点击监听器，避免重复设置
                positiveButton.setOnClickListener(null)
                positiveButton.setOnClickListener {
                    android.util.Log.e("GroupInfoActivity", "🔥🔥🔥 确定按钮被点击 - selectedFriends: ${selectedFriends.size} 个")
                    android.util.Log.e("GroupInfoActivity", "🔥🔥🔥 selectedFriends 内容: $selectedFriends")
                    
                    // 双重验证：检查 selectedFriends 是否为空
                    if (selectedFriends.isEmpty()) {
                        android.util.Log.e("GroupInfoActivity", "❌❌❌ selectedFriends 为空，不允许添加成员，直接返回")
                        Toast.makeText(this@GroupInfoActivity, "请至少选择一个好友", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    val memberIds = selectedFriends.toList()
                    android.util.Log.e("GroupInfoActivity", "🔥🔥🔥 准备添加成员 - memberIds: $memberIds, 数量: ${memberIds.size}")
                    
                    // 再次验证 memberIds 不为空
                    if (memberIds.isEmpty()) {
                        android.util.Log.e("GroupInfoActivity", "❌❌❌ memberIds 转换后为空，这不应该发生，直接返回")
                        Toast.makeText(this@GroupInfoActivity, "请至少选择一个好友", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    // 验证 memberIds 中没有空字符串
                    val validMemberIds = memberIds.filter { it.isNotBlank() }
                    if (validMemberIds.isEmpty()) {
                        android.util.Log.e("GroupInfoActivity", "❌❌❌ memberIds 中所有ID都无效（空字符串），直接返回")
                        Toast.makeText(this@GroupInfoActivity, "请至少选择一个好友", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    if (validMemberIds.size != memberIds.size) {
                        android.util.Log.w("GroupInfoActivity", "⚠️ memberIds 中有无效ID，已过滤 - 原始: ${memberIds.size}, 有效: ${validMemberIds.size}")
                    }
                    
                    android.util.Log.e("GroupInfoActivity", "✅✅✅ 验证通过，调用 viewModel.addMembers - groupId: $groupId, memberIds: $validMemberIds")
                    // 调用 API 添加成员
                    viewModel.addMembers(groupId, validMemberIds)
                    dialog.dismiss()
                }
            }
            
            dialog.show()
        }
    }
    
    private fun showRemoveMemberDialog(member: com.tongxun.data.remote.dto.GroupMemberDto) {
        if (member.role == "OWNER") {
            Toast.makeText(this, "不能删除群主", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("删除成员")
            .setMessage("确定要将 ${member.nickname} 移出群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                viewModel.removeMember(groupId, member.userId)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDisbandGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("解散群组")
            .setMessage("确定要解散群组吗？解散后所有成员将被移出，此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                viewModel.disbandGroup(groupId)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // 菜单会在 onPrepareOptionsMenu 中动态更新
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        // 只有群主且群组未解散时才显示解散群组菜单
        if (viewModel.isOwner && !viewModel.isDisbanded.value) {
            menuInflater.inflate(com.tongxun.R.menu.menu_group_info, menu)
        }
        return super.onPrepareOptionsMenu(menu)
    }
    
    private fun updateMenu() {
        invalidateOptionsMenu()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            com.tongxun.R.id.menu_disband_group -> {
                showDisbandGroupDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

