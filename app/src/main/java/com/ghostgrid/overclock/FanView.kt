
package com.ghostgrid.overclock

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.cos
import kotlin.math.sin

/** Lays out children in an arc fan, anchored at the bottom-right corner. */
class FanView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : ViewGroup(ctx, attrs) {

    var radius = 260
    var startAngle = 160   // degrees
    var endAngle = 250     // fans upward-left from the edge anchor

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxW = 0
        var maxH = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            maxW = maxOf(maxW, child.measuredWidth)
            maxH = maxOf(maxH, child.measuredHeight)
        }
        val size = radius * 2 + maxOf(maxW, maxH)
        setMeasuredDimension(size, size)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount == 0) return
        val anchorX = r - l - 40
        val anchorY = b - t - 40
        val step = if (childCount > 1) (endAngle - startAngle).toFloat() / (childCount - 1) else 0f
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val angle = Math.toRadians((startAngle + step * i).toDouble())
            val cx = anchorX + (radius * cos(angle)).toInt()
            val cy = anchorY + (radius * sin(angle)).toInt()
            val halfW = child.measuredWidth / 2
            val halfH = child.measuredHeight / 2
            child.layout(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        }
    }
}
