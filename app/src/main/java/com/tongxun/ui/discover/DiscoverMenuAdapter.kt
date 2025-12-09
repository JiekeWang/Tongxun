package com.tongxun.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tongxun.databinding.ItemDiscoverMenuBinding

class DiscoverMenuAdapter(
    private val onItemClick: (DiscoverMenuItem) -> Unit
) : ListAdapter<DiscoverMenuItem, DiscoverMenuAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiscoverMenuBinding.inflate(
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
        private val binding: ItemDiscoverMenuBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DiscoverMenuItem) {
            binding.apply {
                tvTitle.text = item.title
                ivIcon.setImageResource(item.iconRes)
                
                root.setOnClickListener {
                    onItemClick(item)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DiscoverMenuItem>() {
        override fun areItemsTheSame(oldItem: DiscoverMenuItem, newItem: DiscoverMenuItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DiscoverMenuItem, newItem: DiscoverMenuItem): Boolean {
            return oldItem == newItem
        }
    }
}

data class DiscoverMenuItem(
    val id: String,
    val title: String,
    val iconRes: Int,
    val onClick: () -> Unit
)

