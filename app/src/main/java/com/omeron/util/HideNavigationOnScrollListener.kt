package com.omeron.util

import androidx.recyclerview.widget.RecyclerView
import com.omeron.UiViewModel

/**
 * Hides the bottom bar while a list scrolls down and shows it again on scroll up. Lists inside a
 * MotionLayout never reach the bar's CoordinatorLayout behavior through nested scrolling, so
 * they drive the bar through [UiViewModel] instead.
 */
class HideNavigationOnScrollListener(
    private val uiViewModel: UiViewModel
) : RecyclerView.OnScrollListener() {

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (dy > 0 && uiViewModel.navigationVisibility.value) {
            uiViewModel.setNavigationVisibility(false)
        } else if (dy < 0 && !uiViewModel.navigationVisibility.value) {
            uiViewModel.setNavigationVisibility(true)
        }
    }
}
