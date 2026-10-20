package com.omeron.ui.profile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ProfileTab
import com.omeron.ui.common.fragment.ListFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A tab of the profile screen: a list kept on this device (saved posts, saved comments or
 * history) that needs no refresh and opens its media in the viewer.
 */
abstract class ProfileTabFragment<T : RecyclerView.Adapter<out RecyclerView.ViewHolder>> :
    ListFragment<T>() {

    override val viewModel: ProfileViewModel by hiltNavGraphViewModels(R.id.profile)

    override val enablePullToRefresh: Boolean
        get() = false

    protected abstract val tab: ProfileTab

    protected val selectionViewModel: ProfileSelectionViewModel by activityViewModels()

    /** The posts the tab shows now, in the order the viewer pages through them. */
    protected abstract fun currentPosts(): List<PostEntity>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateContentView()
        // Ticking a row only changes its look; a fade between the two looks would flicker.
        (binding.listContent.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    /** Passes this tab's selection mode and selected ids to [show] each time they change. */
    protected fun showSelection(show: (isActive: Boolean, selectedIds: Set<String>) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            selectionViewModel.selection
                .map { selection -> selection.isActive(tab) to selection.selectedIds(tab) }
                .distinctUntilChanged()
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { (isActive, selectedIds) -> show(isActive, selectedIds) }
        }
    }

    private fun updateContentView() {
        // Update empty data view to be higher than usual
        val contentMargin = resources.getDimension(R.dimen.profile_content_margin).toInt()

        binding.loadingState.run {
            ConstraintSet().apply {
                clone(root)
                clear(textEmptyData.id, ConstraintSet.BOTTOM)
                applyTo(root)
            }

            emptyData.updateLayoutParams<MarginLayoutParams> { topMargin = contentMargin }
        }
    }

    protected fun showEmptyState(isEmpty: Boolean) {
        binding.loadingState.run {
            emptyData.isVisible = isEmpty
            textEmptyData.isVisible = isEmpty
        }
    }

    protected fun openMedia(post: PostEntity) {
        openFilmstripViewerOnPosts(post, currentPosts())
    }

    // Image, gallery and video posts all open the viewer on this tab's media posts.
    // Each tab registers its own back callback after the profile screen does, so Back reaches the
    // tab first. The profile screen decides what Back means (leave selection, close search, or go
    // up), so the tab hands it over instead of navigating up itself.
    override fun onBackPressed() {
        val profileFragment = checkNotNull(parentFragment as? ProfileFragment) {
            "A profile tab must be hosted by ProfileFragment, was $parentFragment"
        }
        profileFragment.handleBackFromTab()
    }

    override fun onImageClick(post: PostEntity) {
        openMedia(post)
    }

    override fun onVideoClick(post: PostEntity) {
        openMedia(post)
    }
}
