package com.omeron.ui.user

import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.paging.PagingData
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.data.repository.PostListRepository
import com.omeron.ui.common.fragment.PagingListFragment
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.util.extension.launchRepeat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserPostFragment : PagingListFragment<PostListAdapter, PostEntity>() {

    override val viewModel: UserViewModel by hiltNavGraphViewModels(R.id.user)

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
        }
    }

    override fun createPagingAdapter(): PostListAdapter {
        return PostListAdapter(repository, this, this)
    }
}
