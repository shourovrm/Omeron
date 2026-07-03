package com.omeron.ui.subreddit

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omeron.data.local.mapper.PostMapper2
import com.omeron.data.model.Data
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.TimeSorting
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SubredditSearchViewModel @Inject constructor(
    private val repository: PostListRepository,
    preferencesRepository: PreferencesRepository,
    private val postMapper: PostMapper2,
    @DispatchersModule.DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> =
        preferencesRepository.getContentPreferences()

    private val _sorting: MutableStateFlow<Sorting> = MutableStateFlow(DEFAULT_SORTING)
    val sorting: StateFlow<Sorting> = _sorting

    private val _query: MutableStateFlow<String> = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _subreddit: MutableStateFlow<String> = MutableStateFlow("")
    val subreddit: StateFlow<String> = _subreddit

    val postDataFlow: Flow<PagingData<PostEntity>>

    val searchData: StateFlow<Data.Fetch> = combine(
        query,
        sorting
    ) { query, sorting ->
        Data.Fetch(query, sorting)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Data.Fetch("", DEFAULT_SORTING)
    )

    private var latestUser: Data.User? = null

    private val userData: Flow<Data.User> = combine(
        historyIds, savedPostIds, contentPreferences
    ) { history, saved, prefs ->
        Data.User(history, saved, prefs)
    }.onEach {
        latestUser = it
    }.distinctUntilChangedBy {
        it.contentPreferences
    }

    init {
        postDataFlow = searchData
            .dropWhile { it.query.isBlank() }
            .flatMapLatest { searchData -> userData.map { searchData to it } }
            .flatMapLatest { data -> getPosts(data.first, data.second) }
            .cachedIn(viewModelScope)
    }

    private fun getPosts(
        data: Data.Fetch,
        user: Data.User
    ): Flow<PagingData<PostEntity>> {
        // TODO: Check subreddit value is not blank
        return repository.searchInSubreddit(data.query, subreddit.value, data.sorting)
            .map { pagingData ->
                PostUtil.filterPosts(pagingData, latestUser ?: user, postMapper, defaultDispatcher)
            }
    }

    fun setQuery(subreddit: String) {
        _query.updateValue(subreddit)
    }

    fun setSorting(sorting: Sorting) {
        _sorting.updateValue(sorting)
    }

    fun setSubreddit(subreddit: String) {
        _subreddit.updateValue(subreddit)
    }

    companion object {
        private val DEFAULT_SORTING = Sorting(Sort.RELEVANCE, TimeSorting.ALL)
    }
}
