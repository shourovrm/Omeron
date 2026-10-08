package com.omeron.ui.profile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.ui.common.fragment.ListFragment

/**
 * A tab of the profile screen: a list kept on this device (saved posts, saved comments or
 * history) that needs no refresh and opens its media in the viewer.
 */
abstract class ProfileTabFragment<T : RecyclerView.Adapter<out RecyclerView.ViewHolder>> :
    ListFragment<T>() {

    override val viewModel: ProfileViewModel by hiltNavGraphViewModels(R.id.profile)

    override val enablePullToRefresh: Boolean
        get() = false

    /** The posts the tab shows now, in the order the viewer pages through them. */
    protected abstract fun currentPosts(): List<PostEntity>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateContentView()
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
    override fun onImageClick(post: PostEntity) {
        openMedia(post)
    }

    override fun onVideoClick(post: PostEntity) {
        openMedia(post)
    }
}
