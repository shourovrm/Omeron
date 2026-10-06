package com.omeron.ui.filmstrip

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps the Filmstrip pager and turns a vertical swipe on the media into a post change.
 *
 * The pages consume every touch, so the gesture is classified in [onInterceptTouchEvent] from
 * the moves that pass through on their way to the page. Only a single-finger gesture whose
 * vertical travel clearly dominates is claimed; everything else, including taps and horizontal
 * paging, is left alone. Once the child pager or a zoomed image asks for the gesture with
 * requestDisallowInterceptTouchEvent, the framework stops calling the intercept, so those
 * requests win without any extra code here.
 *
 * Touches on the info block, seek bar and rail never get here because those views are siblings
 * drawn above the pager.
 */
class VerticalPostSwipeLayout @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null
) : FrameLayout(context, attributes) {

    /** Asked once per gesture, when it first looks vertical; false leaves the gesture to the page. */
    var canSwipeToPost: () -> Boolean = { true }

    var onPostSwipe: (PostSwipeDirection) -> Unit = {}

    /** Reports the finger's vertical offset from where the claimed swipe started. */
    var onSwipeDrag: (verticalOffset: Float) -> Unit = {}

    /** The claimed swipe ended without changing post; whatever followed the finger goes back. */
    var onSwipeAbandoned: () -> Unit = {}

    private enum class GestureState { UNDECIDED, IGNORED, CLAIMED }

    private val viewConfiguration = ViewConfiguration.get(context)
    private val touchSlop = viewConfiguration.scaledTouchSlop

    // A deliberate swipe is longer than the slop that merely starts it.
    private val distanceThreshold = touchSlop * DISTANCE_THRESHOLD_IN_SLOPS

    private val flingVelocityThreshold =
        viewConfiguration.scaledMaximumFlingVelocity / FLING_VELOCITY_DIVISOR

    private var gestureState = GestureState.IGNORED
    private var downX = 0F
    private var downY = 0F
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Pinch zoom and two-finger pans belong to the page.
                if (gestureState == GestureState.UNDECIDED) gestureState = GestureState.IGNORED
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureState == GestureState.UNDECIDED) {
                    velocityTracker?.addMovement(event)
                    classifyGesture(event)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endGesture()
        }
        return gestureState == GestureState.CLAIMED
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only reached with events the pages did not take, so a gesture that was not claimed
        // here has nothing to do with this view.
        if (gestureState != GestureState.CLAIMED) return false

        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> onSwipeDrag(event.y - downY)
            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureState = GestureState.IGNORED
                onSwipeAbandoned()
            }
            MotionEvent.ACTION_UP -> {
                completeGesture(event)
                endGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                onSwipeAbandoned()
                endGesture()
            }
        }
        return true
    }

    private fun startGesture(event: MotionEvent) {
        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
        downX = event.x
        downY = event.y
        gestureState = GestureState.UNDECIDED
    }

    private fun classifyGesture(event: MotionEvent) {
        val horizontalTravel = abs(event.x - downX)
        val verticalTravel = abs(event.y - downY)
        if (maxOf(horizontalTravel, verticalTravel) <= touchSlop) return

        val isVertical = verticalTravel > VERTICAL_DOMINANCE_RATIO * horizontalTravel
        gestureState = if (isVertical && canSwipeToPost()) {
            // Ancestors must not take a gesture that is already a post swipe.
            parent?.requestDisallowInterceptTouchEvent(true)
            GestureState.CLAIMED
        } else {
            GestureState.IGNORED
        }
    }

    private fun completeGesture(releaseEvent: MotionEvent) {
        val verticalTravel = releaseEvent.y - downY
        val tracker = velocityTracker ?: return onSwipeAbandoned()
        tracker.computeCurrentVelocity(
            VELOCITY_UNITS_PER_SECOND,
            viewConfiguration.scaledMaximumFlingVelocity.toFloat()
        )
        val verticalVelocity = tracker.yVelocity

        // A fling that reverses the finger's net direction is a wobble, not a swipe.
        val isFlingInTravelDirection = verticalTravel * verticalVelocity > 0F &&
            abs(verticalVelocity) >= flingVelocityThreshold
        val isFarEnough = abs(verticalTravel) >= distanceThreshold
        if (!isFarEnough && !isFlingInTravelDirection) return onSwipeAbandoned()

        onPostSwipe(if (verticalTravel < 0F) PostSwipeDirection.NEXT else PostSwipeDirection.PREVIOUS)
    }

    private fun endGesture() {
        gestureState = GestureState.IGNORED
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private companion object {
        // The vertical travel must be at least this many times the horizontal travel.
        const val VERTICAL_DOMINANCE_RATIO = 1.5F

        const val DISTANCE_THRESHOLD_IN_SLOPS = 6

        // A flick is a fraction of the system's fastest allowed fling.
        const val FLING_VELOCITY_DIVISOR = 10

        const val VELOCITY_UNITS_PER_SECOND = 1000
    }
}
