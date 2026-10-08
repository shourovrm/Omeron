package com.omeron.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.omeron.data.local.mapper.PostMapper2
import com.omeron.data.local.mapper.SubredditMapper2
import com.omeron.data.local.mapper.UserMapper2
import com.omeron.data.model.Data
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.TimeSorting
import com.omeron.data.model.User
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.SubredditEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.remote.api.reddit.model.AboutChild
import com.omeron.data.remote.api.reddit.model.AboutUserChild
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.PostUtil
import com.omeron.util.SearchUtil
import com.omeron.util.extension.latest
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
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
    private val userMapper: UserMapper2,
    // Kept by the navigation graph across process death, which the view model itself is not.
    private val savedStateHandle: SavedStateHandle,
    @DispatchersModule.DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> =
        preferencesRepository.getContentPreferences()

    // Search results aren't scoped to one subreddit, so this is always the global default.
    val postLayout: Flow<PostLayout> =
        preferencesRepository.getPostLayout()

    private val _sorting: MutableStateFlow<Sorting> = MutableStateFlow(DEFAULT_SORTING)
    val sorting: StateFlow<Sorting> = _sorting

    private val _query: MutableStateFlow<String> =
        MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())
    val query: StateFlow<String> get() = _query

    // What is in the search field, searched or not: the communities are filtered by it.
    private val typedText = MutableStateFlow("")

    private val recentQueries: Flow<List<String>> = preferencesRepository.getRecentSearchQueries()

    private val visibleCommunityNames: Flow<List<String>> = currentProfile.flatMapLatest {
        repository.getVisibleSubscriptionsNames(it.id)
    }

    /** Recent queries and the matching communities, shown while no query is searched. */
    val suggestions: Flow<List<SearchSuggestionItem>> = combine(
        recentQueries,
        visibleCommunityNames,
        typedText
    ) { recent, communityNames, typed ->
        buildSearchSuggestions(recent, SearchUtil.filterCommunityNames(communityNames, typed))
    }

    // Lowercase, because the stored names ignore case and a result may spell a name differently.
    // Hidden communities are still joined, so every subscription counts.
    val joinedCommunityNames: StateFlow<Set<String>> = subscriptionsNames
        .map { names -> names.map { it.lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _lastRefreshPost: MutableStateFlow<Long> =
        MutableStateFlow(System.currentTimeMillis())
    val lastRefreshPost: StateFlow<Long> = _lastRefreshPost.asStateFlow()

    private val _lastRefreshSubreddit: MutableStateFlow<Long> =
        MutableStateFlow(System.currentTimeMillis())
    val lastRefreshSubreddit: StateFlow<Long> = _lastRefreshSubreddit.asStateFlow()

    private val _lastRefreshUser: MutableStateFlow<Long> =
        MutableStateFlow(System.currentTimeMillis())
    val lastRefreshUser: StateFlow<Long> = _lastRefreshUser.asStateFlow()

    val postDataFlow: Flow<PagingData<PostEntity>>
    val subredditDataFlow: Flow<PagingData<SubredditEntity>>
    val userDataFlow: Flow<PagingData<User>>

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

    // A blank query (the field was cleared) must not search for nothing; the last results stay
    // untouched until the next real query.
    val data: Flow<Pair<Data.Fetch, Data.User>> = searchData
        .filter { it.query.isNotBlank() }
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

        userDataFlow = data
            .flatMapLatest { data -> getUsers(data.first, data.second) }
            .onEach { _lastRefreshUser.value = System.currentTimeMillis() }
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

    private fun getUsers(
        data: Data.Fetch,
        user: Data.User
    ): Flow<PagingData<User>> {
        return repository.searchUser(data.query, data.sorting)
            .map { pagingData ->
                pagingData
                    .map { userMapper.dataToEntity((it as AboutUserChild).data) }
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
        savedStateHandle[KEY_QUERY] = query
        _query.updateValue(query)
    }

    fun setTypedText(text: String) {
        typedText.value = text
    }

    /** Searches [query] and remembers it in the recent queries. */
    fun searchFor(query: String) {
        setQuery(query)
        viewModelScope.launch { preferencesRepository.addRecentSearchQuery(query) }
    }

    fun removeRecentQuery(query: String) {
        viewModelScope.launch { preferencesRepository.removeRecentSearchQuery(query) }
    }

    fun toggleSubscription(subredditName: String, icon: String?) {
        viewModelScope.launch {
            val profileId = currentProfile.latest?.id ?: return@launch
            if (subredditName.lowercase() in joinedCommunityNames.value) {
                repository.unsubscribe(subredditName, profileId)
            } else {
                repository.subscribe(subredditName, profileId, icon)
            }
        }
    }

    companion object {
        private const val KEY_QUERY = "KEY_QUERY"

        private val DEFAULT_SORTING = Sorting(Sort.RELEVANCE, TimeSorting.ALL)
    }
}
