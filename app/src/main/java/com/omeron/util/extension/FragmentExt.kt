package com.omeron.util.extension

import android.view.View
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.omeron.UnredditApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun Fragment.launchRepeat(state: Lifecycle.State, block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(state) {
            block()
        }
    }
}

/**
 * Keeps the bottom padding of [view] equal to the height of the bottom bar, which overlays the
 * screen, so the last list items can be scrolled clear of it. [view] must not clip to padding.
 */
fun Fragment.keepClearOfBottomNavigation(view: View, bottomNavigationHeight: Flow<Int>) {
    launchRepeat(Lifecycle.State.STARTED) {
        bottomNavigationHeight.collect { height -> view.updatePadding(bottom = height) }
    }
}

val Fragment.unredditApplication: UnredditApplication?
    get() = activity?.unredditApplication
