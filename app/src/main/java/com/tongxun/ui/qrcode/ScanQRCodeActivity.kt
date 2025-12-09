package com.tongxun.ui.qrcode

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.tongxun.databinding.ActivityScanQrcodeBinding
import com.tongxun.domain.repository.FriendRepository
import com.tongxun.ui.contact.AddFriendDialog
import com.tongxun.utils.QRCodeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScanQRCodeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScanQrcodeBinding
    private val viewModel: ScanQRCodeViewModel by viewModels()
    
    @Inject
    lateinit var friendRepository: FriendRepository
    
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val qrData = QRCodeUtils.parseQRContent(result.contents)
            if (qrData != null && qrData.type == "add_friend") {
                // 显示添加好友对话框
                showAddFriendDialog(qrData.userId, qrData.nickname)
            } else {
                Toast.makeText(this, "无效的二维码", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        startScan()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun startScan() {
        val scanOptions = QRCodeUtils.createScanOptions()
        scanLauncher.launch(scanOptions)
    }
    
    private fun showAddFriendDialog(userId: String, nickname: String?) {
        val dialog = AddFriendDialog.newInstance(userId, nickname ?: "")
        dialog.show(supportFragmentManager, "AddFriendDialog")
    }
}

