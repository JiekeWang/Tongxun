package com.tongxun.ui.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tongxun.data.remote.dto.GroupMemberDto
import com.tongxun.databinding.ItemGroupMemberBinding
import com.tongxun.utils.ImageUrlUtils

class GroupMemberAdapter(
    private val currentUserId: String,
    private var isOwner: Boolean,
    private var isAdmin: Boolean,
    private val onMemberClick: (GroupMemberDto) -> Unit,
    private val onMemberLongClick: (GroupMemberDto) -> Unit
) : ListAdapter<GroupMemberDto, GroupMemberAdapter.ViewHolder>(DiffCallback()) {
    
    fun updatePermissions(isOwner: Boolean, isAdmin: Boolean) {
        this.isOwner = isOwner
        this.isAdmin = isAdmin
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupMemberBinding.inflate(
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
        private val binding: ItemGroupMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(member: GroupMemberDto) {
            binding.tvMemberName.text = member.nickname
            
            // 显示角色标签
            when (member.role) {
                "OWNER" -> {
                    binding.tvRole.text = "群主"
                    binding.tvRole.visibility = android.view.View.VISIBLE
                }
                "ADMIN" -> {
                    binding.tvRole.text = "管理员"
                    binding.tvRole.visibility = android.view.View.VISIBLE
                }
                else -> {
                    binding.tvRole.visibility = android.view.View.GONE
                }
            }
            
            // 加载头像
            val avatarUrl = member.avatar?.let { ImageUrlUtils.getFullImageUrl(it) }
            if (avatarUrl != null) {
                Glide.with(binding.root.context)
                    .load(avatarUrl)
                    .placeholder(com.tongxun.R.drawable.ic_launcher_round)
                    .into(binding.ivMemberAvatar)
            } else {
                binding.ivMemberAvatar.setImageResource(com.tongxun.R.drawable.ic_launcher_round)
            }
            
            // 设置点击事件
            binding.root.setOnClickListener {
                onMemberClick(member)
            }
            
            // 设置删除按钮（仅群主/管理员可以删除成员，且不能删除自己或群主）
            val canDelete = (isOwner || isAdmin) && 
                           member.userId != currentUserId && 
                           member.role != "OWNER"
            
            binding.btnDelete.visibility = if (canDelete) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            
            binding.btnDelete.setOnClickListener {
                if (canDelete) {
                    onMemberLongClick(member)
                }
            }
            
            // 保留长按事件作为备用
            binding.root.setOnLongClickListener {
                if (canDelete) {
                    onMemberLongClick(member)
                    true
                } else {
                    false
                }
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<GroupMemberDto>() {
        override fun areItemsTheSame(oldItem: GroupMemberDto, newItem: GroupMemberDto): Boolean {
            return oldItem.userId == newItem.userId
        }
        
        override fun areContentsTheSame(oldItem: GroupMemberDto, newItem: GroupMemberDto): Boolean {
            return oldItem == newItem
        }
    }
}

