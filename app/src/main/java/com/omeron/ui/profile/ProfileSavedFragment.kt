package com.omeron.ui.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.Comment
import com.omeron.data.model.SavedItem
import com.omeron.data.model.db.PostEntity
import com.omeron.ui.commentmenu.CommentMenuFragment
import com.omeron.ui.postdetails.PostDetailsFragment
import com.omeron.ui.postmenu.PostMenuFragment
import com.omeron.ui.user.UserCommentsAdapter
import com.omeron.util.extension.currentNavigationFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ponytail: two thin leaf fragments (below) pick the tab's flow, since
// FragmentAdapter.Page instantiates via Class.newInstance() with no args.
// Base is not @AndroidEntryPoint - Hilt only needs it on the leaf classes.
abstract class ProfileSavedFragment : ProfileTabFragment<ProfileSavedAdapter>(),
    UserCommentsAdapter.CommentClickListener {

    protected abstract val savedFlow: Flow<List<SavedItem>>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViewModel()
    }

    override fun currentPosts(): List<PostEntity> = adapter.currentPosts()

    /** Called with every list the tab shows, after it was handed to the adapter. */
    protected open fun onItemsShown(items: List<SavedItem>) {
        // Nothing by default
    }

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(savedFlow, viewModel.contentPreferences) { items, preferences ->
                adapter.contentPreferences = preferences
                adapter.submitList(items)
                showEmptyState(items.isEmpty())
                onItemsShown(items)
            }.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collect()
        }
    }

    override fun onClick(comment: Comment.CommentEntity) {
        activity?.currentNavigationFragment
            ?.parentFragmentManager
            ?.beginTransaction()
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            ?.add(
                R.id.fragment_container,
                PostDetailsFragment.newInstance(comment.permalink),
                PostDetailsFragment.TAG
            )
            ?.addToBackStack(null)
            ?.commit()
    }

    override fun onLongClick(comment: Comment.CommentEntity) {
        CommentMenuFragment.show(
            parentFragmentManager,
            comment,
            CommentMenuFragment.MenuType.DETAILS
        )
    }

    override fun createAdapter(): ProfileSavedAdapter {
        return ProfileSavedAdapter(requireContext(), this, this, this)
    }
}

@AndroidEntryPoint
class ProfileSavedPostsFragment : ProfileSavedFragment() {
    override val savedFlow: Flow<List<SavedItem>> get() = viewModel.savedPosts

    private val savedCountAdapter = ProfileSavedCountAdapter()

    override fun attachedAdapter(adapter: ProfileSavedAdapter): RecyclerView.Adapter<out RecyclerView.ViewHolder> {
        return ConcatAdapter(savedCountAdapter, adapter)
    }

    override fun onItemsShown(items: List<SavedItem>) {
        savedCountAdapter.savedCount = items.size
    }

    override fun onLongClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post, PostMenuFragment.MenuType.SAVED)
    }
}

@AndroidEntryPoint
class ProfileSavedCommentsFragment : ProfileSavedFragment() {
    override val savedFlow: Flow<List<SavedItem>> get() = viewModel.savedComments
}
