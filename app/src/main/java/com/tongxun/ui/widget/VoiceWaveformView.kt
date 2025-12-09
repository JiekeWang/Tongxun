package com.tongxun.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.tongxun.R

/**
 * 语音波形视图，类似微信的语音消息波形显示
 * 支持静态显示和播放时的动态动画
 */
class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isPlaying = false
    private var barCount = 4 // 波形条数
    private var barWidth = 0f
    private var barSpacing = 0f
    private var maxBarHeight = 0f
    private var minBarHeight = 0f
    
    private val barHeights = FloatArray(4) { 0f }
    private val barAnimators = mutableListOf<ValueAnimator>()
    
    private var paint: Paint
    private var barColor = ContextCompat.getColor(context, R.color.primary)
    private var staticBarColor = ContextCompat.getColor(context, R.color.text_secondary)
    
    init {
        paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }
        
        // 设置默认颜色
        barColor = ContextCompat.getColor(context, R.color.primary)
        staticBarColor = ContextCompat.getColor(context, R.color.text_secondary)
        barCount = 4
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        maxBarHeight = h * 0.8f
        minBarHeight = h * 0.3f
        
        // 计算每个条的宽度和间距
        val totalSpacing = (barCount - 1) * 4.dpToPx()
        val availableWidth = w - totalSpacing
        barWidth = availableWidth / barCount
        barSpacing = 4.dpToPx()
        
        // 初始化静态高度（不同高度，看起来更自然）
        if (!isPlaying) {
            barHeights[0] = maxBarHeight * 0.6f
            barHeights[1] = maxBarHeight * 0.8f
            barHeights[2] = maxBarHeight * 0.5f
            barHeights[3] = maxBarHeight * 0.7f
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentColor = if (isPlaying) barColor else staticBarColor
        paint.color = currentColor
        paint.strokeWidth = barWidth
        
        val centerY = height / 2f
        
        for (i in 0 until barCount) {
            val x = (i * (barWidth + barSpacing)) + barWidth / 2
            val barHeight = barHeights[i]
            
            // 绘制圆形端点（类似微信效果）
            canvas.drawCircle(x, centerY - barHeight / 2, barWidth / 2, paint)
            canvas.drawCircle(x, centerY + barHeight / 2, barWidth / 2, paint)
            
            // 绘制竖条
            canvas.drawLine(
                x, centerY - barHeight / 2,
                x, centerY + barHeight / 2,
                paint
            )
        }
    }
    
    /**
     * 设置播放状态
     */
    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        
        isPlaying = playing
        
        if (playing) {
            startAnimation()
        } else {
            stopAnimation()
            // 恢复到静态高度
            barHeights[0] = maxBarHeight * 0.6f
            barHeights[1] = maxBarHeight * 0.8f
            barHeights[2] = maxBarHeight * 0.5f
            barHeights[3] = maxBarHeight * 0.7f
            invalidate()
        }
    }
    
    /**
     * 开始播放动画
     */
    private fun startAnimation() {
        stopAnimation()
        
        // 为每个条创建独立的动画
        for (i in 0 until barCount) {
            // 随机化每个条的高度范围，让动画更自然
            val randomFactor = 0.7f + (i % 3) * 0.1f
            val adjustedMaxHeight = maxBarHeight * randomFactor
            val adjustedMinHeight = minBarHeight * (0.5f + (i % 2) * 0.2f)
            
            val delay = i * 100L // 错开动画时间，形成波浪效果
            
            val animator = ValueAnimator.ofFloat(adjustedMinHeight, adjustedMaxHeight).apply {
                duration = (400 + i * 50).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                startDelay = delay
                
                addUpdateListener { animation ->
                    barHeights[i] = animation.animatedValue as Float
                    invalidate()
                }
            }
            
            animator.start()
            barAnimators.add(animator)
        }
    }
    
    /**
     * 停止播放动画
     */
    private fun stopAnimation() {
        barAnimators.forEach { it.cancel() }
        barAnimators.clear()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
    
    private fun Int.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
}
