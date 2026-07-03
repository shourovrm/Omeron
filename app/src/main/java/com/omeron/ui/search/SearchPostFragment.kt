package com.omeron.ui.search

import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.ui.common.fragment.PagingListFragment
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.util.extension.launchRepeat
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
                    binding.listContent.layoutManager = when (layout) {
                        PostLayout.GALLERY ->
                            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                        PostLayout.CARD -> LinearLayoutManager(requireContext())
                    }
                }
            }
        }
    }

    override fun createPagingAdapter(): PostListAdapter {
        return PostListAdapter(repository, this, this)
    }
}
