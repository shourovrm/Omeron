package com.omeron.ui.common.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import coil.imageLoader
import coil.request.ImageRequest
import com.omeron.util.UrlDrawable
import kotlin.math.min

class RedditTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var scrollEnabled: Boolean = false

    // ImageSpan drawables have no view attached; route their invalidations (gif frames) here.
    private val inlineImageCallback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) = invalidate()

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            postDelayed(what, `when` - SystemClock.uptimeMillis())
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            removeCallbacks(what)
        }
    }

    init {
        highlightColor = Color.TRANSPARENT
        isClickable = false
        isFocusable = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }

    fun setText(text: CharSequence, enableScroll: Boolean) {
        scrollEnabled = enableScroll
        this.text = text
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        (text as? Spanned)?.let(::loadInlineImages)
    }

    private fun loadInlineImages(spanned: Spanned) {
        for (span in spanned.getSpans(0, spanned.length, ImageSpan::class.java)) {
            val urlDrawable = span.drawable as? UrlDrawable ?: continue
            if (urlDrawable.delegate != null) continue

            urlDrawable.callback = inlineImageCallback

            val request = ImageRequest.Builder(context)
                .data(urlDrawable.url)
                .target(onSuccess = { result -> applyInlineImage(urlDrawable, result) })
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    private fun applyInlineImage(urlDrawable: UrlDrawable, result: Drawable) {
        val density = resources.displayMetrics.density
        val maxWidth = (if (width > 0) width - paddingLeft - paddingRight else 0)
            .takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels * 3 / 4)

        val intrinsicWidth = result.intrinsicWidth.coerceAtLeast(1)
        val intrinsicHeight = result.intrinsicHeight.coerceAtLeast(1)
        val scale = min(density, maxWidth.toFloat() / intrinsicWidth)

        val targetWidth = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val targetHeight = (intrinsicHeight * scale).toInt().coerceAtLeast(1)

        result.setBounds(0, 0, targetWidth, targetHeight)
        urlDrawable.setBounds(0, 0, targetWidth, targetHeight)
        urlDrawable.delegate = result

        // Bounds changed after layout; re-set the text so the ImageSpan re-measures.
        text = text
    }

    override fun scrollTo(x: Int, y: Int) {
        if (scrollEnabled) {
            super.scrollTo(x, y)
        }
    }
}
