package com.tongxun.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tongxun.ui.contact.ContactFragment
import com.tongxun.ui.discover.DiscoverFragment
import com.tongxun.ui.home.HomeFragment
import com.tongxun.ui.me.MeFragment

class MainPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 4
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> ContactFragment()
            2 -> DiscoverFragment()
            3 -> MeFragment()
            else -> HomeFragment()
        }
    }
}

