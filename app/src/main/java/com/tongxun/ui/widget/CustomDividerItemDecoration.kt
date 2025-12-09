package com.tongxun.ui.widget

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 自定义分割线装饰器，支持设置左侧偏移量
 */
class CustomDividerItemDecoration(
    private val divider: Drawable,
    private val leftOffset: Int = 0
) : RecyclerView.ItemDecoration() {
    
    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft + leftOffset
        val right = parent.width - parent.paddingRight
        
        val childCount = parent.childCount
        for (i in 0 until childCount - 1) { // 最后一个item不需要分割线
            val child = parent.getChildAt(i)
            if (child == null) continue
            
            val params = child.layoutParams as? RecyclerView.LayoutParams ?: continue
            
            val top = child.bottom + params.bottomMargin
            val dividerHeight = if (divider.intrinsicHeight > 0) {
                divider.intrinsicHeight
            } else {
                (1 * parent.resources.displayMetrics.density).toInt() // 默认1dp
            }
            val bottom = top + dividerHeight
            
            divider.setBounds(left, top, right, bottom)
            divider.draw(canvas)
        }
    }
    
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        // 为每个item底部添加分割线的高度（除了最后一个）
        val position = parent.getChildAdapterPosition(view)
        if (position < state.itemCount - 1) {
            outRect.bottom = divider.intrinsicHeight
        }
    }
}

