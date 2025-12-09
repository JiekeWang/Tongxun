package com.tongxun.ui.contact

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tongxun.databinding.DialogAddFriendBinding

class AddFriendDialog : DialogFragment() {
    
    private var _binding: DialogAddFriendBinding? = null
    private val binding get() = _binding!!
    
    private var userId: String? = null
    private var nickname: String? = null
    private var onAddFriendListener: ((String?) -> Unit)? = null
    
    companion object {
        private const val ARG_USER_ID = "user_id"
        private const val ARG_NICKNAME = "nickname"
        
        fun newInstance(userId: String, nickname: String): AddFriendDialog {
            return AddFriendDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                    putString(ARG_NICKNAME, nickname)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString(ARG_USER_ID)
            nickname = it.getString(ARG_NICKNAME)
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddFriendBinding.inflate(layoutInflater)
        
        nickname?.let {
            binding.tvUserName.text = it
        }
        userId?.let {
            binding.tvUserId.text = "ID: $it"
        }
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加好友")
            .setView(binding.root)
            .setPositiveButton("发送") { _, _ ->
                val message = binding.etMessage.text.toString().trim().takeIf { it.isNotBlank() }
                onAddFriendListener?.invoke(message)
            }
            .setNegativeButton("取消", null)
            .create()
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
    
    fun setOnAddFriendListener(listener: (String?) -> Unit) {
        this.onAddFriendListener = listener
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

