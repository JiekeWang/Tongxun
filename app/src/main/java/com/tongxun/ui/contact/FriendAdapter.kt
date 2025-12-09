package com.tongxun.ui.contact

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tongxun.data.local.entity.FriendEntity
import com.tongxun.databinding.ItemFriendBinding
import com.tongxun.utils.ImageUrlUtils

class FriendAdapter(
    private val onItemClick: (FriendEntity) -> Unit
) : ListAdapter<FriendEntity, FriendAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendBinding.inflate(
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
        private val binding: ItemFriendBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(friend: FriendEntity) {
            // 优先显示备注，其次是昵称，最后是好友ID
            binding.tvName.text = friend.remark 
                ?: friend.nickname 
                ?: friend.friendId
            
            // 加载头像
            loadAvatar(friend.avatar, binding.ivAvatar)
            
            binding.root.setOnClickListener {
                onItemClick(friend)
            }
        }
        
        /**
         * 加载头像
         */
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
    
    class DiffCallback : DiffUtil.ItemCallback<FriendEntity>() {
        override fun areItemsTheSame(oldItem: FriendEntity, newItem: FriendEntity): Boolean {
            return oldItem.friendId == newItem.friendId
        }
        
        override fun areContentsTheSame(oldItem: FriendEntity, newItem: FriendEntity): Boolean {
            return oldItem == newItem
        }
    }
}

