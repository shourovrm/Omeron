package com.omeron.ui.search

import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.paging.PagingData
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.ui.common.FilmstripGapItemDecoration
import com.omeron.ui.common.fragment.PagingListFragment
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.util.extension.currentNavigationFragment
import com.omeron.util.extension.filteredForLayout
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.layoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchPostFragment : PagingListFragment<PostListAdapter, PostEntity>() {

    override val viewModel: SearchViewModel by hiltNavGraphViewModels(R.id.search)

    // Results are pointers without media data, so the viewer's own lookup decides what a
    // candidate really is.
    override val filmstripMediaPredicate: (PostEntity) -> Boolean = PostEntity::isFilmstripSearchCandidate

    override val flow: Flow<PagingData<PostEntity>>
        get() = viewModel.postDataFlow.filteredForLayout(viewModel.postLayout, filmstripMediaPredicate)

    override val showItemDecoration: Boolean
        get() = true

    override val bottomOverlayHeight: Int
        get() = resources.getDimensionPixelSize(
            com.google.android.material.R.dimen.design_bottom_navigation_height
        )

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
                    binding.listContent.layoutManager = layout.layoutManager(requireContext()) { adapter.itemCount }
                }
            }
        }
    }

    override fun createPagingAdapter(): PostListAdapter {
        return PostListAdapter(repository, this, this)
    }

    override fun initRecyclerView() {
        super.initRecyclerView()
        // The shared list is a plain RecyclerView, so the grid's tile gaps are added here.
        binding.listContent.addItemDecoration(FilmstripGapItemDecoration(requireContext()))
        resumeFilmstripFeed(adapter, binding.listContent)
    }

    // Same nested-pager rule as onClick: the viewer goes on the NavHost's FragmentManager. The
    // viewer fetches each result's full post itself, since the result has no media of its own.
    override fun onFilmstripClick(post: PostEntity) {
        activity?.currentNavigationFragment?.let { currentFragment ->
            openFilmstripViewer(
                post,
                adapter,
                binding.listContent,
                currentFragment.parentFragmentManager
            )
        }
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

    // A candidate's media tap opens the viewer, which fetches the full post. A result that is not
    // a candidate (or a link preview) carries no media url, so the default handlers would open
    // empty media and force-close; it opens the post page instead, which re-fetches the post.
    override fun onImageClick(post: PostEntity) = openInFilmstripOrPostPage(post)

    override fun onVideoClick(post: PostEntity) = openInFilmstripOrPostPage(post)

    override fun onLinkClick(post: PostEntity) = onClick(post)

    private fun openInFilmstripOrPostPage(post: PostEntity) {
        if (!openInFilmstripIfMedia(post)) onClick(post)
    }
}
