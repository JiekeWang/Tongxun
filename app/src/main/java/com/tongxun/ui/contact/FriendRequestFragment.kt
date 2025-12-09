package com.tongxun.ui.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tongxun.databinding.FragmentFriendRequestBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FriendRequestFragment : Fragment() {
    
    private var _binding: FragmentFriendRequestBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: FriendRequestViewModel by viewModels()
    private lateinit var requestAdapter: FriendRequestAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendRequestBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        viewModel.loadFriendRequests()
    }
    
    private fun setupRecyclerView() {
        requestAdapter = FriendRequestAdapter(
            onAccept = { requestId ->
                viewModel.acceptRequest(requestId)
            },
            onReject = { requestId ->
                viewModel.rejectRequest(requestId)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = requestAdapter
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.friendRequests.collect { requests ->
                if (requests.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    requestAdapter.submitList(requests)
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                state.error?.let { error ->
                    try {
                        val context = context
                        if (context != null && isAdded) {
                            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        viewModel.clearError()
                    } catch (e: Exception) {
                        android.util.Log.e("FriendRequestFragment", "显示错误消息失败", e)
                    }
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadFriendRequests()
            binding.swipeRefresh.isRefreshing = false
        }
        
        // 设置返回按钮
        binding.toolbar.setNavigationOnClickListener {
            // 返回到上一个Fragment（ContactFragment）
            parentFragmentManager.popBackStack()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

