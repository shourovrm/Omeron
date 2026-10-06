package com.omeron.ui.mediaviewer

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

/**
 * Loads a full-screen viewer image. The callbacks mirror Coil's request listener so each viewer
 * decides what loading and failure look like.
 */
fun ImageView.loadViewerImage(
    url: String,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onError: () -> Unit,
    onSuccess: () -> Unit
) {
    Coil.imageLoader(context).enqueue(
        ImageRequest.Builder(context).apply {
            data(url)
            crossfade(true)
            scale(Scale.FILL)
            precision(Precision.AUTOMATIC)
            memoryCachePolicy(CachePolicy.READ_ONLY)
            diskCachePolicy(CachePolicy.READ_ONLY)
            listener(
                onStart = { onStart() },
                onCancel = { onCancel() },
                onError = { _, _ -> onError() },
                onSuccess = { _, _ -> onSuccess() }
            )
            target { drawable -> setImageDrawable(drawable) }
        }.build()
    )
}

/**
 * Keeps a zoomed image's pan gestures away from the surrounding pager: while the image is zoomed
 * and can still scroll both ways, or while two fingers are down, the pager may not intercept.
 */
@SuppressLint("ClickableViewAccessibility")
class ZoomableImageTouchListener(private val image: View) : View.OnTouchListener {

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return if (
            event.pointerCount >= 2 ||
            view.canScrollHorizontally(1) &&
            image.canScrollHorizontally(-1)
        ) {
            // Multi-touch event
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Disallow RecyclerView to intercept touch events.
                    image.parent.requestDisallowInterceptTouchEvent(true)
                    // Disable touch on view
                    false
                }
                MotionEvent.ACTION_UP -> {
                    // Allow RecyclerView to intercept touch events.
                    image.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        } else {
            true
        }
    }
}
