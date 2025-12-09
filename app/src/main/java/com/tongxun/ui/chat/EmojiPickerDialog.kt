package com.tongxun.ui.chat

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tongxun.R
import com.tongxun.databinding.DialogEmojiPickerBinding

/**
 * 表情选择器对话框
 */
class EmojiPickerDialog : DialogFragment() {
    
    private var _binding: DialogEmojiPickerBinding? = null
    private val binding get() = _binding!!
    
    private var onEmojiSelectedListener: ((String) -> Unit)? = null
    
    // 常用表情列表
    private val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
        "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
        "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
        "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
        "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨",
        "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥",
        "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
        "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
        "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑",
        "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻",
        "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸",
        "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
    )
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEmojiPickerBinding.inflate(layoutInflater)
        
        val recyclerView = binding.recyclerView
        recyclerView.layoutManager = GridLayoutManager(context, 8)
        recyclerView.adapter = EmojiAdapter(emojis) { emoji ->
            onEmojiSelectedListener?.invoke(emoji)
            dismiss()
        }
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setTitle("选择表情")
            .setNegativeButton("取消", null)
            .create()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    fun setOnEmojiSelectedListener(listener: (String) -> Unit) {
        onEmojiSelectedListener = listener
    }
    
    private class EmojiAdapter(
        private val emojis: List<String>,
        private val onEmojiClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_emoji, parent, false)
            return EmojiViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.bind(emojis[position])
        }
        
        override fun getItemCount() = emojis.size
        
        inner class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val emojiView: TextView = itemView.findViewById(R.id.emojiView)
            
            init {
                itemView.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        onEmojiClick(emojis[position])
                    }
                }
            }
            
            fun bind(emoji: String) {
                emojiView.text = emoji
            }
        }
    }
}

