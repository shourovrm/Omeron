package com.omeron.ui.search

import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.paging.PagingData
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.ui.common.fragment.PagingListFragment
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.util.extension.currentNavigationFragment
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.layoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchPostFragment : PagingListFragment<PostListAdapter, PostEntity>() {

    override val viewModel: SearchViewModel by hiltNavGraphViewModels(R.id.search)

    override val flow: Flow<PagingData<PostEntity>>
        get() = viewModel.postDataFlow

    override val showItemDecoration: Boolean
        get() = true

    @Inject
    lateinit var repository: PostListRepository

    // Guards against layoutManager reassignment on same-value emissions, which resets scroll
    // position.
    private var appliedPostLayout: PostLayout? = null

    override fun bindViewModel() {
        super.bindViewModel()
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.contentPreferences.collect {
                    adapter.contentPreferences = it
                }
            }

            launch {
                viewModel.lastRefreshPost.collect {
                    setRefreshTime(it)
                }
            }

            launch {
                viewModel.postLayout.collect { layout ->
                    adapter.postLayout = layout
                    if (appliedPostLayout == layout) return@collect
                    appliedPostLayout = layout
                    binding.listContent.layoutManager = layout.layoutManager(requireContext())
                }
            }
        }
    }

    override fun createPagingAdapter(): PostListAdapter {
        return PostListAdapter(repository, this, this)
    }

    // A search result is just a permalink pointer with no media data of its own. Opening it reuses
    // Omeron's universal post opener (PostDetailsFragment), which re-fetches the full post by
    // permalink through the normal scraper engine and renders images/videos/comments like a
    // subreddit post. This fragment is nested in a ViewPager, so the transaction must run on the
    // NavHost's FragmentManager (which owns R.id.fragment_container) - same pattern as
    // ProfileSavedFragment - not this fragment's own parentFragmentManager.
    override fun onClick(post: PostEntity) {
        val hostFragmentManager = activity?.currentNavigationFragment?.parentFragmentManager ?: return
        onClick(hostFragmentManager, post)
    }

    // In card/gallery layout the tile's media is tappable, but a search result carries no media
    // url, so the default handlers would open empty media and force-close. Route every media tap
    // to the post detail (same as the card tap) so it opens the real, re-fetched post instead.
    override fun onImageClick(post: PostEntity) = onClick(post)

    override fun onVideoClick(post: PostEntity) = onClick(post)

    override fun onLinkClick(post: PostEntity) = onClick(post)
}
