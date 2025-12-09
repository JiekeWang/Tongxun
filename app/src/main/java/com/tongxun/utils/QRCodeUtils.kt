package com.tongxun.utils

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanOptions
import android.graphics.Color
import android.content.Context
import android.widget.ImageView
import coil.load
import org.json.JSONObject

object QRCodeUtils {
    
    /**
     * 生成二维码Bitmap
     */
    fun generateQRCode(
        content: String,
        width: Int = 500,
        height: Int = 500
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
            }
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 生成用户二维码内容（用于添加好友）
     */
    fun generateUserQRContent(userId: String, nickname: String? = null): String {
        val json = JSONObject().apply {
            put("type", "add_friend")
            put("userId", userId)
            nickname?.let { put("nickname", it) }
            put("timestamp", System.currentTimeMillis())
        }
        return json.toString()
    }
    
    /**
     * 解析二维码内容
     */
    fun parseQRContent(content: String): QRCodeData? {
        return try {
            val json = JSONObject(content)
            val type = json.optString("type")
            val userId = json.optString("userId")
            val nickname = json.optString("nickname", "").takeIf { it.isNotBlank() }
            val timestamp = json.optLong("timestamp", 0)
            
            QRCodeData(
                type = type,
                userId = userId,
                nickname = nickname,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 创建扫描选项
     */
    fun createScanOptions(): ScanOptions {
        return ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("扫描二维码")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
    }
    
    data class QRCodeData(
        val type: String,
        val userId: String,
        val nickname: String?,
        val timestamp: Long
    )
}

