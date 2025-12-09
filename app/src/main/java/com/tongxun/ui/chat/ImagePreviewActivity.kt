package com.tongxun.ui.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tongxun.databinding.ActivityImagePreviewBinding
import coil.load

class ImagePreviewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityImagePreviewBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val imageUrl = intent.getStringExtra("image_url") ?: return
        
        // 转换为完整URL
        val fullImageUrl = com.tongxun.utils.ImageUrlUtils.getFullImageUrl(imageUrl) ?: imageUrl
        
        binding.ivImage.load(fullImageUrl) {
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_report_image)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}

