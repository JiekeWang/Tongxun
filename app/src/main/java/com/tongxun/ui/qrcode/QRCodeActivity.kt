package com.tongxun.ui.qrcode

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tongxun.databinding.ActivityQrcodeBinding
import com.tongxun.domain.repository.AuthRepository
import com.tongxun.utils.QRCodeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QRCodeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityQrcodeBinding
    private val viewModel: QRCodeViewModel by viewModels()
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        generateQRCode()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun generateQRCode() {
        lifecycleScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser != null) {
                val qrContent = QRCodeUtils.generateUserQRContent(
                    currentUser.userId,
                    currentUser.nickname
                )
                val qrBitmap = QRCodeUtils.generateQRCode(qrContent, 500, 500)
                if (qrBitmap != null) {
                    binding.ivQRCode.setImageBitmap(qrBitmap)
                    binding.tvUserId.text = "用户ID: ${currentUser.userId}"
                    binding.tvNickname.text = "昵称: ${currentUser.nickname}"
                } else {
                    Toast.makeText(this@QRCodeActivity, "生成二维码失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

