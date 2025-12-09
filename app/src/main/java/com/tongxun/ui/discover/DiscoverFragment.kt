package com.tongxun.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.tongxun.R
import com.tongxun.databinding.FragmentDiscoverBinding

class DiscoverFragment : Fragment() {
    
    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private lateinit var menuAdapter: DiscoverMenuAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupMenuItems()
    }
    
    private fun setupRecyclerView() {
        menuAdapter = DiscoverMenuAdapter { item ->
            item.onClick()
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = menuAdapter
            // 移除默认的 clipToPadding，让分割线可以延伸到边缘
            clipToPadding = false
            clipChildren = false
        }
    }
    
    private fun setupMenuItems() {
        val menuItems = listOf(
            DiscoverMenuItem(
                id = "moments",
                title = getString(R.string.moments),
                iconRes = android.R.drawable.ic_menu_gallery, // 暂时使用系统图标，后续可以替换为自定义图标
                onClick = {
                    // TODO: 打开朋友圈页面
                    Toast.makeText(requireContext(), "朋友圈功能开发中", Toast.LENGTH_SHORT).show()
                }
            ),
            DiscoverMenuItem(
                id = "games",
                title = getString(R.string.mini_games),
                iconRes = android.R.drawable.ic_menu_view, // 暂时使用系统图标，后续可以替换为自定义图标
                onClick = {
                    // TODO: 打开小游戏页面
                    Toast.makeText(requireContext(), "小游戏功能开发中", Toast.LENGTH_SHORT).show()
                }
            )
        )
        
        menuAdapter.submitList(menuItems)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

