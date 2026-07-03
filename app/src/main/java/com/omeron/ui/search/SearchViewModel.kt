package com.omeron.ui.search

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.omeron.data.local.mapper.PostMapper2
import com.omeron.data.local.mapper.SubredditMapper2
import com.omeron.data.model.Data
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.TimeSorting
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.SubredditEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.remote.api.reddit.model.AboutChild
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.PostUtil
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PostListRepository,
    private val preferencesRepository: PreferencesRepository,
    private val postMapper: PostMapper2,
    private val subredditMapper: SubredditMapper2,
    @DispatchersModule.DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> =
        preferencesRepository.getContentPreferences()

    // Search results aren't scoped to one subreddit, so this is always the global default.
    val postLayout: Flow<PostLayout> = preferencesRepository.getPostLayout()

    private val _sorting: MutableStateFlow<Sorting> = MutableStateFlow(DEFAULT_SORTING)
    val sorting: StateFlow<Sorting> = _sorting

    private val _query: MutableStateFlow<String> = MutableStateFlow("")
    val query: StateFlow<String> get() = _query

    private val _lastRefreshPost: MutableStateFlow<Long> =
        MutableStateFlow(System.currentTimeMillis())
    val lastRefreshPost: StateFlow<Long> = _lastRefreshPost.asStateFlow()

    private val _lastRefreshSubreddit: MutableStateFlow<Long> =
        MutableStateFlow(System.currentTimeMillis())
    val lastRefreshSubreddit: StateFlow<Long> = _lastRefreshSubreddit.asStateFlow()

    val postDataFlow: Flow<PagingData<PostEntity>>
    val subredditDataFlow: Flow<PagingData<SubredditEntity>>

    private val searchData: StateFlow<Data.Fetch> = combine(
        query,
        sorting
    ) { query, sorting ->
        Data.Fetch(query, sorting)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Data.Fetch("", DEFAULT_SORTING)
    )

    private val userData: Flow<Data.User> = combine(
        historyIds,
        savedPostIds,
        contentPreferences
    ) { history, saved, prefs ->
        Data.User(history, saved, prefs)
    }

    val data: Flow<Pair<Data.Fetch, Data.User>> = searchData
        .dropWhile { it.query.isBlank() }
        .flatMapLatest { searchData -> userData.take(1).map { searchData to it } }

    init {
        postDataFlow = data
            .flatMapLatest { data -> getPosts(data.first, data.second) }
            .onEach { _lastRefreshPost.value = System.currentTimeMillis() }
            .cachedIn(viewModelScope)

        subredditDataFlow = data
            .flatMapLatest { data -> getSubreddits(data.first, data.second) }
            .onEach { _lastRefreshSubreddit.value = System.currentTimeMillis() }
            .cachedIn(viewModelScope)
    }

    private fun getPosts(
        data: Data.Fetch,
        user: Data.User
    ): Flow<PagingData<PostEntity>> {
        return repository.searchPost(data.query, data.sorting)
            .map { pagingData ->
                PostUtil.filterPosts(pagingData, user, postMapper, defaultDispatcher)
            }
    }

    private fun getSubreddits(
        data: Data.Fetch,
        user: Data.User
    ): Flow<PagingData<SubredditEntity>> {
        return repository.searchSubreddit(data.query, data.sorting)
            .map { pagingData ->
                pagingData
                    .map { subredditMapper.dataToEntity((it as AboutChild).data) }
                    .filter { user.contentPreferences.showNsfw || !it.over18 }
            }
            .flowOn(defaultDispatcher)
    }

    fun setSorting(sorting: Sorting) {
        _sorting.updateValue(sorting)
    }

    fun setPostLayout(layout: PostLayout) {
        viewModelScope.launch { preferencesRepository.setPostLayout(layout = layout) }
    }

    fun setQuery(query: String) {
        _query.updateValue(query)
    }

    companion object {
        private val DEFAULT_SORTING = Sorting(Sort.RELEVANCE, TimeSorting.ALL)
    }
}
