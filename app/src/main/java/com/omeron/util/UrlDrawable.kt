package com.omeron.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable

/**
 * Placeholder returned by [HtmlParser]'s ImageGetter for inline `<img>` tags (gifs/emotes in
 * comments). Starts empty; [RedditTextView][com.omeron.ui.common.widget.RedditTextView] loads
 * the [url] with Coil and swaps in the result via [delegate].
 */
class UrlDrawable(val url: String) : Drawable(), Drawable.Callback {

    var delegate: Drawable? = null
        set(value) {
            field?.callback = null
            field = value
            value?.callback = this
            (value as? Animatable)?.start()
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        delegate?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        delegate?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        delegate?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // Forward animation ticks from the delegate (gif frames) up to our own callback (the view).
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
