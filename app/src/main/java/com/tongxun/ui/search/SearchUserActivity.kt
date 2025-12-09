package com.tongxun.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tongxun.databinding.ActivitySearchUserBinding
import com.tongxun.ui.contact.AddFriendDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchUserActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySearchUserBinding
    private val viewModel: SearchUserViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchUserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                binding.btnSearch.isEnabled = !state.isLoading && state.searchText.isNotBlank()
                
                state.error?.let { error ->
                    Toast.makeText(this@SearchUserActivity, error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
                
                state.searchResult?.let { user ->
                    showSearchResult(user)
                    // 搜索新用户时隐藏跳转按钮
                    binding.btnGoToFriendRequest.visibility = View.GONE
                } ?: run {
                    binding.layoutResult.visibility = View.GONE
                    binding.btnGoToFriendRequest.visibility = View.GONE
                }
                
                if (state.friendRequestSent) {
                    Toast.makeText(this@SearchUserActivity, "好友请求已发送", Toast.LENGTH_SHORT).show()
                    // 显示底部跳转按钮
                    binding.btnGoToFriendRequest.visibility = View.VISIBLE
                    viewModel.clearFriendRequestSent()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSearch.setOnClickListener {
            val searchText = binding.etSearch.text.toString().trim()
            if (searchText.isNotBlank()) {
                viewModel.searchUser(searchText)
            }
        }
        
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchText(s?.toString() ?: "")
                binding.layoutResult.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        binding.btnAddFriend.setOnClickListener {
            val user = viewModel.uiState.value.searchResult
            if (user != null) {
                showAddFriendDialog(user)
            }
        }
        
        binding.btnGoToFriendRequest.setOnClickListener {
            // 跳转到MainActivity的联系人页面，并显示好友请求Fragment
            val intent = Intent(this@SearchUserActivity, com.tongxun.ui.main.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_friend_request", true)
            }
            startActivity(intent)
            finish()
        }
    }
    
    private fun showSearchResult(user: com.tongxun.data.local.entity.UserEntity) {
        binding.layoutResult.visibility = View.VISIBLE
        binding.tvUserName.text = user.nickname
        binding.tvUserId.text = "ID: ${user.userId}"
        binding.tvPhoneNumber.text = user.phoneNumber
        
        // 检查是否已经是好友
        viewModel.checkIfFriend(user.userId) { isFriend ->
            binding.btnAddFriend.visibility = if (isFriend) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }
    
    private fun showAddFriendDialog(user: com.tongxun.data.local.entity.UserEntity) {
        val dialog = AddFriendDialog.newInstance(user.userId, user.nickname)
        dialog.setOnAddFriendListener { message ->
            viewModel.sendFriendRequest(user.userId, message)
        }
        dialog.show(supportFragmentManager, "AddFriendDialog")
    }
}

