package com.tongxun.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tongxun.databinding.FragmentHomeBinding
import com.tongxun.ui.chat.ChatActivity
import com.tongxun.domain.repository.GroupRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var conversationAdapter: ConversationAdapter
    
    @Inject
    lateinit var groupRepository: GroupRepository
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        android.util.Log.e("HomeFragment", "🔥🔥🔥 HomeFragment.onViewCreated() 被调用")
        
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.toolbar.findViewById<android.widget.ImageView>(com.tongxun.R.id.btnSearch)?.setOnClickListener {
            // TODO: 打开搜索界面
            android.widget.Toast.makeText(requireContext(), "搜索功能开发中", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        binding.toolbar.findViewById<android.widget.ImageView>(com.tongxun.R.id.btnAdd)?.setOnClickListener {
            // 显示添加菜单
            android.app.AlertDialog.Builder(requireContext())
                .setItems(arrayOf("发起群聊", "添加朋友", "搜索群组", "扫一扫")) { _, which ->
                    when (which) {
                        0 -> {
                            // 发起群聊
                            val intent = Intent(requireContext(), com.tongxun.ui.group.CreateGroupActivity::class.java)
                            startActivity(intent)
                        }
                        1 -> {
                            // 添加朋友
                            val intent = Intent(requireContext(), com.tongxun.ui.search.SearchUserActivity::class.java)
                            startActivity(intent)
                        }
                        2 -> {
                            // 搜索群组
                            val intent = Intent(requireContext(), com.tongxun.ui.search.SearchGroupActivity::class.java)
                            startActivity(intent)
                        }
                        3 -> {
                            // 扫一扫
                            val intent = Intent(requireContext(), com.tongxun.ui.qrcode.ScanQRCodeActivity::class.java)
                            startActivity(intent)
                        }
                    }
                }
                .show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.e("HomeFragment", "🔥🔥🔥 HomeFragment.onResume() 被调用 - 消息页已显示")
        // 注意：消息页只显示本地数据库的会话列表
        // 实时消息通过 WebSocket 接收（MainViewModel 管理）
        // 离线消息在 MainViewModel 初始化时拉取
    }
    
    private fun setupRecyclerView() {
        conversationAdapter = ConversationAdapter(
            onItemClick = { conversation ->
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("conversation_id", conversation.conversationId)
                    putExtra("target_id", conversation.targetId)
                    putExtra("target_name", conversation.targetName)
                }
                startActivity(intent)
            },
            onTopClick = { conversation ->
                viewModel.setTopStatus(conversation.conversationId, !conversation.isTop)
            },
            onMutedClick = { conversation ->
                viewModel.setMutedStatus(conversation.conversationId, !conversation.isMuted)
            },
            onDeleteClick = { conversation ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("删除会话")
                    .setMessage("确定要删除与${conversation.targetName}的会话吗？")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteConversation(conversation.conversationId)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            getGroupMemberAvatars = { groupId ->
                // 获取群成员头像列表
                try {
                    val membersResult = groupRepository.getGroupMembers(groupId)
                    if (membersResult.isSuccess) {
                        membersResult.getOrNull()?.map { it.avatar } ?: emptyList()
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("HomeFragment", "获取群成员头像失败 - groupId: $groupId", e)
                    emptyList()
                }
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = conversationAdapter
            // 添加列表分割线，从名称位置开始（头像48dp + 间距12dp + 左边距16dp = 76dp）
            val dividerDrawable = androidx.core.content.ContextCompat.getDrawable(
                requireContext(),
                com.tongxun.R.drawable.divider_list
            )
            if (dividerDrawable != null) {
                val leftOffset = ((48 + 12 + 16) * requireContext().resources.displayMetrics.density).toInt() // 头像宽度 + 间距 + 左边距
                addItemDecoration(
                    com.tongxun.ui.widget.CustomDividerItemDecoration(
                        dividerDrawable,
                        leftOffset
                    )
                )
            }
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.conversations.collect { conversations ->
                android.util.Log.e("HomeFragment", "收到会话列表更新 - 共 ${conversations.size} 个会话")
                if (conversations.isEmpty()) {
                    android.util.Log.w("HomeFragment", "会话列表为空，显示空状态")
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    android.util.Log.d("HomeFragment", "会话列表不为空，更新RecyclerView - ${conversations.size} 个")
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    conversationAdapter.submitList(conversations) {
                        android.util.Log.d("HomeFragment", "RecyclerView列表已更新")
                    }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

