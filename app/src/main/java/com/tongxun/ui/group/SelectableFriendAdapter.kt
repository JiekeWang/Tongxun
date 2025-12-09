package com.tongxun.ui.group

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tongxun.data.local.entity.FriendEntity
import com.tongxun.databinding.ItemSelectableFriendBinding
import com.tongxun.utils.ImageUrlUtils

class SelectableFriendAdapter(
    private val onItemClick: (FriendEntity) -> Unit
) : ListAdapter<SelectableFriend, SelectableFriendAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectableFriendBinding.inflate(
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
        private val binding: ItemSelectableFriendBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(selectableFriend: SelectableFriend) {
            val friend = selectableFriend.friend
            // 优先显示备注，其次是昵称，最后是好友ID
            binding.tvName.text = friend.remark 
                ?: friend.nickname 
                ?: friend.friendId
            
            // 加载头像
            loadAvatar(friend.avatar, binding.ivAvatar)
            
            // 显示选择状态
            binding.checkbox.isChecked = selectableFriend.isSelected
            
            binding.root.setOnClickListener {
                onItemClick(friend)
            }
        }
        
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
    }
    
    class DiffCallback : DiffUtil.ItemCallback<SelectableFriend>() {
        override fun areItemsTheSame(oldItem: SelectableFriend, newItem: SelectableFriend): Boolean {
            return oldItem.friend.friendId == newItem.friend.friendId
        }
        
        override fun areContentsTheSame(oldItem: SelectableFriend, newItem: SelectableFriend): Boolean {
            return oldItem == newItem
        }
    }
}

data class SelectableFriend(
    val friend: FriendEntity,
    val isSelected: Boolean = false
)
