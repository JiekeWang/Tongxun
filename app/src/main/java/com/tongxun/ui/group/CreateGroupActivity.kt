package com.tongxun.ui.group

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tongxun.databinding.ActivityCreateGroupBinding
import com.tongxun.ui.chat.ChatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateGroupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCreateGroupBinding
    private val viewModel: CreateGroupViewModel by viewModels()
    private lateinit var friendAdapter: SelectableFriendAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        android.util.Log.d("CreateGroupActivity", "onCreate - 初始化创建群组界面")
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("CreateGroupActivity", "onResume - 同步好友列表")
        // 每次进入页面时同步好友列表，确保数据是最新的
        viewModel.syncFriends()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "发起群聊"
    }
    
    private fun setupRecyclerView() {
        friendAdapter = SelectableFriendAdapter { friend ->
            // 切换选择状态
            viewModel.toggleFriendSelection(friend.friendId)
        }
        
        binding.recyclerViewFriends.apply {
            layoutManager = LinearLayoutManager(this@CreateGroupActivity)
            adapter = friendAdapter
        }
    }
    
    private fun setupObservers() {
        // 观察好友列表
        lifecycleScope.launch {
            viewModel.friends.collect { friends ->
                android.util.Log.d("CreateGroupActivity", "收到好友列表更新 - 共 ${friends.size} 个好友")
                if (friends.isEmpty()) {
                    android.util.Log.w("CreateGroupActivity", "好友列表为空，显示空状态")
                    binding.tvEmpty.visibility = android.view.View.VISIBLE
                    binding.recyclerViewFriends.visibility = android.view.View.GONE
                } else {
                    android.util.Log.d("CreateGroupActivity", "好友列表不为空，更新RecyclerView - ${friends.size} 个")
                    binding.tvEmpty.visibility = android.view.View.GONE
                    binding.recyclerViewFriends.visibility = android.view.View.VISIBLE
                    friendAdapter.submitList(friends) {
                        android.util.Log.d("CreateGroupActivity", "RecyclerView列表已更新")
                    }
                }
            }
        }
        
        // 观察已选择的好友数量
        lifecycleScope.launch {
            viewModel.selectedFriends.collect {
                updateSelectedCount()
            }
        }
        
        // 观察创建群组的结果
        lifecycleScope.launch {
            viewModel.createGroupResult.collect { result ->
                result?.let {
                    if (it.isSuccess) {
                        val group = it.getOrNull()
                        group?.let { groupDto ->
                            Toast.makeText(this@CreateGroupActivity, "群聊创建成功", Toast.LENGTH_SHORT).show()
                            // 跳转到群聊界面
                            // 注意：群聊的conversationId就是群组ID，不需要添加任何前缀
                            val intent = Intent(this@CreateGroupActivity, ChatActivity::class.java).apply {
                                putExtra("conversation_id", groupDto.groupId)
                                putExtra("target_id", groupDto.groupId)
                                putExtra("target_name", groupDto.groupName)
                                putExtra("conversation_type", "GROUP")
                            }
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        val error = it.exceptionOrNull()
                        Toast.makeText(
                            this@CreateGroupActivity,
                            "创建群聊失败: ${error?.message ?: "未知错误"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        
        // 观察加载状态
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }
    
    private fun updateSelectedCount() {
        val selectedCount = viewModel.selectedFriends.value.size
        binding.tvSelectedCount.text = "已选择 $selectedCount 人"
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.tongxun.R.menu.menu_create_group, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            com.tongxun.R.id.menu_create -> {
                showCreateGroupDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showCreateGroupDialog() {
        val selectedFriends = viewModel.selectedFriends.value
        // 验证：群总人数（包含自己）至少需要3人
        val totalMembers = selectedFriends.size + 1 // +1 是包含创建者自己
        if (totalMembers < 3) {
            Toast.makeText(this, "群聊至少需要3人（包含自己），请至少选择2位好友", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(com.tongxun.R.layout.dialog_create_group, null)
        val etGroupName = dialogView.findViewById<android.widget.EditText>(com.tongxun.R.id.etGroupName)
        val etDescription = dialogView.findViewById<android.widget.EditText>(com.tongxun.R.id.etDescription)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("创建群聊")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val groupName = etGroupName.text.toString().trim()
                if (groupName.isEmpty()) {
                    Toast.makeText(this, "群名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val description = etDescription.text.toString().trim().takeIf { it.isNotEmpty() }
                viewModel.createGroup(groupName, description, selectedFriends)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
