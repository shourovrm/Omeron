package com.omeron.ui.search

import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import com.omeron.R
import com.omeron.data.model.db.SubredditEntity
import com.omeron.ui.common.fragment.PagingListFragment
import com.omeron.util.extension.launchRepeat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchSubredditFragment : PagingListFragment<SearchSubredditAdapter, SubredditEntity>() {

    override val viewModel: SearchViewModel by hiltNavGraphViewModels(R.id.search)

    override val flow: Flow<PagingData<SubredditEntity>>
        get() = viewModel.subredditDataFlow

    override val bottomOverlayHeight: Int
        get() = resources.getDimensionPixelSize(
            com.google.android.material.R.dimen.design_bottom_navigation_height
        )

    override fun bindViewModel() {
        super.bindViewModel()
        launchRepeat(Lifecycle.State.STARTED) {
            viewModel.joinedCommunityNames.collect { adapter.joinedNames = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lastRefreshSubreddit
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect {
                    setRefreshTime(it)
                }
        }
    }

    override fun createPagingAdapter(): SearchSubredditAdapter {
        return SearchSubredditAdapter(
            onSubredditClick = ::openSubreddit,
            onJoinClick = { subreddit ->
                viewModel.toggleSubscription(subreddit.displayName, subreddit.icon)
            }
        )
    }
}
