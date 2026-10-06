package com.omeron.ui.filmstrip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.omeron.R

/**
 * A row of equal segments showing the position inside a gallery: every segment up to and
 * including the current one is filled.
 */
class FilmstripSegmentsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.filmstrip_segment_inactive)
    }

    private val segmentBounds = RectF()

    private val preferredGapWidth = resources.displayMetrics.density * SEGMENT_GAP_DP

    private var segmentCount = 0
    private var currentIndex = 0

    fun setPosition(currentIndex: Int, segmentCount: Int) {
        if (this.currentIndex == currentIndex && this.segmentCount == segmentCount) return
        this.currentIndex = currentIndex
        this.segmentCount = segmentCount
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (segmentCount == 0) return

        // Long galleries shrink the gap too, otherwise the gaps alone would use up the whole bar.
        val gapWidth = minOf(preferredGapWidth, width / (segmentCount * GAP_SHRINK_DIVISOR))
        val segmentWidth = (width - gapWidth * (segmentCount - 1)) / segmentCount
        val cornerRadius = height / 2F

        for (index in 0 until segmentCount) {
            val left = index * (segmentWidth + gapWidth)
            segmentBounds.set(left, 0F, left + segmentWidth, height.toFloat())
            val paint = if (index <= currentIndex) filledPaint else emptyPaint
            canvas.drawRoundRect(segmentBounds, cornerRadius, cornerRadius, paint)
        }
    }

    private companion object {
        const val SEGMENT_GAP_DP = 4
        const val GAP_SHRINK_DIVISOR = 4F
    }
}
