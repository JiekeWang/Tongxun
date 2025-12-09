package com.tongxun.ui.contact

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tongxun.databinding.FragmentContactBinding
import com.tongxun.ui.profile.UserProfileActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ContactFragment : Fragment() {
    
    private var _binding: FragmentContactBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ContactViewModel by viewModels()
    private lateinit var friendAdapter: FriendAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        // 监听来自MainActivity的跳转请求
        parentFragmentManager.setFragmentResultListener("show_friend_request", this) { _, _ ->
            android.util.Log.d("ContactFragment", "收到显示好友请求的FragmentResult")
            showFriendRequestFragment()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 刷新好友请求数量
        viewModel.loadFriendRequestCount()
        // 强制同步好友列表（确保从好友请求页面返回时能看到新添加的好友）
        viewModel.forceSyncFriends()
        
        // 检查MainActivity的Intent，看是否需要显示好友请求
        activity?.intent?.let { intent ->
            if (intent.getBooleanExtra("show_friend_request", false)) {
                android.util.Log.d("ContactFragment", "onResume中检查到跳转标志，延迟显示好友请求")
                view?.postDelayed({
                    showFriendRequestFragment()
                    // 清除Intent标志
                    intent.removeExtra("show_friend_request")
                }, 300)
            }
        }
    }
    
    private fun showFriendRequestFragment() {
        android.util.Log.d("ContactFragment", "显示好友请求Fragment")
        val fragment = FriendRequestFragment()
        parentFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun setupRecyclerView() {
        friendAdapter = FriendAdapter { friend ->
            val intent = Intent(requireContext(), UserProfileActivity::class.java).apply {
                putExtra("user_id", friend.friendId)
            }
            startActivity(intent)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = friendAdapter
            // 添加列表分割线，从名称位置开始（头像40dp + 间距12dp + 左边距12dp = 64dp）
            val dividerDrawable = androidx.core.content.ContextCompat.getDrawable(
                requireContext(),
                com.tongxun.R.drawable.divider_list
            )
            if (dividerDrawable != null) {
                val leftOffset = ((40 + 12 + 12) * requireContext().resources.displayMetrics.density).toInt() // 头像宽度 + 间距 + 左边距
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
            viewModel.friends.collect { friends ->
                android.util.Log.d("ContactFragment", "收到好友列表更新 - 共 ${friends.size} 个好友")
                if (friends.isEmpty()) {
                    android.util.Log.w("ContactFragment", "好友列表为空，显示空状态")
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    android.util.Log.d("ContactFragment", "好友列表不为空，更新RecyclerView - ${friends.size} 个")
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    friendAdapter.submitList(friends) {
                        android.util.Log.d("ContactFragment", "RecyclerView列表已更新")
                    }
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.fabAddFriend.setOnClickListener {
            // 显示添加好友选项菜单
            android.app.AlertDialog.Builder(requireContext())
                .setItems(arrayOf("搜索用户", "搜索群组", "扫描二维码", "我的二维码")) { _, which ->
                    when (which) {
                        0 -> {
                            // 搜索用户
                            val intent = Intent(requireContext(), com.tongxun.ui.search.SearchUserActivity::class.java)
                            startActivity(intent)
                        }
                        1 -> {
                            // 搜索群组
                            val intent = Intent(requireContext(), com.tongxun.ui.search.SearchGroupActivity::class.java)
                            startActivity(intent)
                        }
                        2 -> {
                            // 扫描二维码
                            val intent = Intent(requireContext(), com.tongxun.ui.qrcode.ScanQRCodeActivity::class.java)
                            startActivity(intent)
                        }
                        3 -> {
                            // 我的二维码
                            val intent = Intent(requireContext(), com.tongxun.ui.qrcode.QRCodeActivity::class.java)
                            startActivity(intent)
                        }
                    }
                }
                .show()
        }
        
        // 添加"新的朋友"入口
        binding.layoutNewFriend.setOnClickListener {
            val fragment = FriendRequestFragment()
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        }
        
        // 观察好友请求数量
        lifecycleScope.launch {
            viewModel.friendRequestCount.collect { count ->
                binding.tvRequestCount.let { tv ->
                    if (count > 0) {
                        tv.visibility = View.VISIBLE
                        tv.text = if (count > 99) "99+" else count.toString()
                    } else {
                        tv.visibility = View.GONE
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

