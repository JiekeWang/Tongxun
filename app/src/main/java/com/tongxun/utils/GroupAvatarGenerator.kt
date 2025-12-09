package com.tongxun.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GroupAvatarGenerator {
    private const val TAG = "GroupAvatarGenerator"
    
    /**
     * 生成群组九宫格头像
     * @param memberAvatars 群成员头像URL列表（最多9个）
     * @param size 最终头像大小（像素）
     * @param callback 生成完成后的回调
     */
    fun generateGroupAvatar(
        context: android.content.Context,
        memberAvatars: List<String?>,
        size: Int = 200,
        callback: (Bitmap?) -> Unit
    ) {
        if (memberAvatars.isEmpty()) {
            callback(null)
            return
        }
        
        // 最多取9个头像
        val avatarsToUse = memberAvatars.take(9)
        
        // 加载所有头像
        // 先初始化列表，确保大小足够
        val bitmaps = MutableList<Bitmap?>(avatarsToUse.size) { null }
        var loadedCount = 0
        val totalCount = avatarsToUse.size
        
        if (totalCount == 0) {
            callback(null)
            return
        }
        
        avatarsToUse.forEachIndexed { index, avatarUrl ->
            val fullUrl = ImageUrlUtils.getFullImageUrl(avatarUrl)
            
            if (fullUrl == null) {
                // 如果没有头像URL，创建一个默认头像
                bitmaps[index] = createDefaultAvatar(size / 3, (index % 26 + 65).toChar().toString())
                loadedCount++
                checkAndGenerate(bitmaps, totalCount, loadedCount, size, callback)
            } else {
                Glide.with(context)
                    .asBitmap()
                    .load(fullUrl)
                    .override(size / 3, size / 3)
                    .centerCrop()
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            bitmaps[index] = resource
                            loadedCount++
                            checkAndGenerate(bitmaps, totalCount, loadedCount, size, callback)
                        }
                        
                        override fun onLoadCleared(placeholder: Drawable?) {
                            // 加载被清除时，使用默认头像
                            bitmaps[index] = createDefaultAvatar(size / 3, (index % 26 + 65).toChar().toString())
                            loadedCount++
                            checkAndGenerate(bitmaps, totalCount, loadedCount, size, callback)
                        }
                    })
            }
        }
    }
    
    /**
     * 检查是否所有头像都已加载完成，如果是则生成九宫格头像
     */
    private fun checkAndGenerate(
        bitmaps: List<Bitmap?>,
        totalCount: Int,
        loadedCount: Int,
        size: Int,
        callback: (Bitmap?) -> Unit
    ) {
        if (loadedCount == totalCount) {
            // 所有头像都已加载，生成九宫格
            val result = createGridAvatar(bitmaps.filterNotNull(), size)
            callback(result)
        }
    }
    
    /**
     * 创建九宫格头像
     */
    private fun createGridAvatar(bitmaps: List<Bitmap>, size: Int): Bitmap {
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val count = bitmaps.size
        // 微信逻辑：<=4人使用2x2田字格，>4人使用3x3九宫格，最多显示9位成员
        val gridSize = if (count <= 4) 2 else 3
        
        val cellSize = size / gridSize
        val spacing = 2 // 单元格之间的间距
        
        // 如果是3个人，需要先绘制顶部背景色
        if (count == 3) {
            // 获取第一个头像的主要颜色作为背景色，或使用默认浅灰色
            val backgroundColor = getDominantColor(bitmaps[0]) ?: 0xFFE5E5E5.toInt()
            paint.color = backgroundColor
            // 绘制顶部背景矩形（整个顶部一行）
            canvas.drawRect(0f, 0f, size.toFloat(), (cellSize + spacing).toFloat(), paint)
        }
        
        // 确定每个头像的位置
        bitmaps.forEachIndexed { index, bitmap ->
            val (row, col, isCenter: Boolean) = when {
                // 3个人：倒品字格布局
                count == 3 -> when (index) {
                    0 -> Triple(0, 0, true) // 第一个：中间上方（居中）
                    1 -> Triple(1, 0, false) // 第二个：左下
                    else -> Triple(1, 1, false) // 第三个：右下
                }
                // <=4人（除了3人），使用2x2网格（田字格）
                count <= 4 -> when (count) {
                    1 -> Triple(0, 0, false) // 1个人：左上角
                    2 -> when (index) {
                        0 -> Triple(0, 0, false) // 第一个：左上
                        else -> Triple(0, 1, false) // 第二个：右上
                    }
                    else -> when (index) {
                        0 -> Triple(0, 0, false) // 第一个：左上
                        1 -> Triple(0, 1, false) // 第二个：右上
                        2 -> Triple(1, 0, false) // 第三个：左下
                        else -> Triple(1, 1, false) // 第四个：右下
                    }
                }
                // >4人（5人及以上），使用3x3网格（九宫格）
                else -> {
                    val r = index / gridSize
                    val c = index % gridSize
                    Triple(r, c, false)
                }
            }
            
            // 计算x坐标：如果是品字格的中间位置，需要居中
            val x = if (isCenter && count == 3 && index == 0) {
                // 倒品字格：第一个头像在中间上方居中显示
                (size - (cellSize - spacing)) / 2f
            } else {
                (col * cellSize + if (col > 0) spacing else 0).toFloat()
            }
            
            val y = (row * cellSize + if (row > 0) spacing else 0).toFloat()
            
            // 缩放bitmap以适应单元格
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, cellSize - spacing, cellSize - spacing, true)
            
            canvas.drawBitmap(scaledBitmap, x, y, paint)
        }
        
        return result
    }
    
    /**
     * 创建默认头像（带字母）
     */
    private fun createDefaultAvatar(size: Int, letter: String): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 背景色（根据字母生成不同颜色）
        val bgColor = getColorForLetter(letter[0])
        canvas.drawColor(bgColor)
        
        // 绘制字母
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val textBounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, textBounds)
        val x = size / 2f
        val y = size / 2f + textBounds.height() / 2f
        
        canvas.drawText(letter, x, y, paint)
        
        return bitmap
    }
    
    /**
     * 根据字母生成颜色
     */
    private fun getColorForLetter(letter: Char): Int {
        val colors = listOf(
            0xFF2196F3.toInt(), // 蓝
            0xFF4CAF50.toInt(), // 绿
            0xFFFF9800.toInt(), // 橙
            0xFFF44336.toInt(), // 红
            0xFF9C27B0.toInt(), // 紫
            0xFF00BCD4.toInt(), // 青
            0xFFFFEB3B.toInt(), // 黄
            0xFF795548.toInt()  // 棕
        )
        val index = (letter.code - 'A'.code) % colors.size
        return colors[index]
    }
    
    /**
     * 获取图片的主要颜色（简化版：取中心区域的平均色，然后调亮作为背景）
     */
    private fun getDominantColor(bitmap: Bitmap): Int? {
        try {
            // 取中心区域的一个小样本
            val sampleSize = 10
            val centerX = bitmap.width / 2
            val centerY = bitmap.height / 2
            val startX = (centerX - sampleSize / 2).coerceAtLeast(0)
            val startY = (centerY - sampleSize / 2).coerceAtLeast(0)
            val endX = (startX + sampleSize).coerceAtMost(bitmap.width)
            val endY = (startY + sampleSize).coerceAtMost(bitmap.height)
            
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0
            
            for (x in startX until endX) {
                for (y in startY until endY) {
                    val pixel = bitmap.getPixel(x, y)
                    rSum += android.graphics.Color.red(pixel)
                    gSum += android.graphics.Color.green(pixel)
                    bSum += android.graphics.Color.blue(pixel)
                    count++
                }
            }
            
            if (count > 0) {
                val r = (rSum / count).toInt()
                val g = (gSum / count).toInt()
                val b = (bSum / count).toInt()
                // 将颜色调亮一些作为背景色（混合白色）
                val lightenFactor = 0.85f
                val lightR = (r * lightenFactor + 255 * (1 - lightenFactor)).toInt().coerceIn(0, 255)
                val lightG = (g * lightenFactor + 255 * (1 - lightenFactor)).toInt().coerceIn(0, 255)
                val lightB = (b * lightenFactor + 255 * (1 - lightenFactor)).toInt().coerceIn(0, 255)
                return android.graphics.Color.rgb(lightR, lightG, lightB)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取主要颜色失败", e)
        }
        return null
    }
}

