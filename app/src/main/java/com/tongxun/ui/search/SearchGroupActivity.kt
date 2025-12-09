package com.tongxun.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tongxun.databinding.ActivitySearchGroupBinding
import com.tongxun.databinding.ItemGroupSearchResultBinding
import com.tongxun.data.remote.dto.GroupDto
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchGroupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySearchGroupBinding
    private val viewModel: SearchGroupViewModel by viewModels()
    private lateinit var adapter: GroupSearchAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = GroupSearchAdapter(
            onApplyClick = { group ->
                showApplyDialog(group)
            },
            onJoinClick = { group ->
                viewModel.joinGroup(group.groupId)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
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
                    Toast.makeText(this@SearchGroupActivity, error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
                
                state.applyResult?.let { result ->
                    Toast.makeText(this@SearchGroupActivity, result, Toast.LENGTH_SHORT).show()
                    viewModel.clearApplyResult()
                }
                
                adapter.submitList(state.searchResults)
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSearch.setOnClickListener {
            val searchText = binding.etSearch.text.toString().trim()
            if (searchText.isNotBlank()) {
                viewModel.searchGroups(searchText)
            }
        }
        
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchText(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun showApplyDialog(group: GroupDto) {
        val editText = android.widget.EditText(this).apply {
            hint = "请输入申请理由（可选）"
            setPadding(32, 16, 32, 16)
        }
        
        AlertDialog.Builder(this)
            .setTitle("申请加入群组")
            .setMessage("群组：${group.groupName}")
            .setView(editText)
            .setPositiveButton("提交") { _, _ ->
                val message = editText.text.toString().trim().takeIf { it.isNotBlank() }
                viewModel.applyToJoinGroup(group.groupId, message)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private class GroupSearchAdapter(
        private val onApplyClick: (GroupDto) -> Unit,
        private val onJoinClick: (GroupDto) -> Unit
    ) : RecyclerView.Adapter<GroupSearchAdapter.ViewHolder>() {
        
        private var groups = emptyList<GroupDto>()
        
        fun submitList(newList: List<GroupDto>) {
            groups = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemGroupSearchResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(groups[position])
        }
        
        override fun getItemCount() = groups.size
        
        inner class ViewHolder(
            private val binding: ItemGroupSearchResultBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(group: GroupDto) {
                binding.tvGroupName.text = group.groupName
                binding.tvMemberCount.text = "${group.memberCount}/${group.maxMemberCount} 人"
                binding.tvDescription.text = group.description ?: "暂无描述"
                binding.tvOwner.text = "群主：${group.ownerId.take(8)}..."
                
                binding.btnApply.setOnClickListener {
                    onApplyClick(group)
                }
                
                binding.btnJoin.setOnClickListener {
                    onJoinClick(group)
                }
            }
        }
    }
}

