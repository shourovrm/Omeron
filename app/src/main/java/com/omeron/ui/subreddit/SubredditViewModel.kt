package com.omeron.ui.subreddit

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omeron.data.local.mapper.PostMapper2
import com.omeron.data.local.mapper.SubredditMapper2
import com.omeron.data.model.Data
import com.omeron.data.model.Resource
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.SubredditEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.PostUtil
import com.omeron.util.extension.latest
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SubredditViewModel @Inject constructor(
    private val repository: PostListRepository,
    private val preferencesRepository: PreferencesRepository,
    private val postMapper: PostMapper2,
    private val subredditMapper: SubredditMapper2,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> =
        preferencesRepository.getContentPreferences()

    private val _sorting: MutableStateFlow<Sorting> = MutableStateFlow(DEFAULT_SORTING)
    val sorting: StateFlow<Sorting> = _sorting

    private val _subreddit: MutableStateFlow<String> = MutableStateFlow("")
    val subreddit: StateFlow<String> = _subreddit

    val postLayout: Flow<PostLayout> = subreddit.flatMapLatest {
        preferencesRepository.getPostLayout(it)
    }

    private val _about: MutableStateFlow<Resource<SubredditEntity>> =
        MutableStateFlow(Resource.Loading())
    val about: StateFlow<Resource<SubredditEntity>> = _about

    private val _isDescriptionCollapsed = MutableStateFlow(true)
    val isDescriptionCollapsed: StateFlow<Boolean> = _isDescriptionCollapsed

    var contentLayoutProgress: Float? = null
    var drawerContentLayoutProgress: Float? = null

    var isSubredditReachable: Boolean = false

    val isSubscribed: StateFlow<Boolean> = combine(
        _subreddit,
        subscriptionsNames
    ) { _subreddit, names ->
        names.any { it.equals(_subreddit, ignoreCase = true) }
    }.flowOn(
        defaultDispatcher
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    private val subredditName: String
        get() = about.value.dataValue?.displayName ?: subreddit.value

    private val icon: String?
        get() = about.value.dataValue?.icon

    val postDataFlow: Flow<PagingData<PostEntity>>

    val searchData: StateFlow<Data.Fetch> = combine(
        subreddit,
        sorting
    ) { subreddit, sorting ->
        Data.Fetch(subreddit, sorting)
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

    private val _lastRefresh: MutableStateFlow<Long> = MutableStateFlow(System.currentTimeMillis())
    val lastRefresh: StateFlow<Long> = _lastRefresh.asStateFlow()

    init {
        postDataFlow = searchData
            .dropWhile { it.query.isBlank() }
            .flatMapLatest { searchData -> userData.map { searchData to it } }
            .flatMapLatest { data -> getPosts(data.first, data.second) }
            .onEach { _lastRefresh.value = System.currentTimeMillis() }
            .cachedIn(viewModelScope)
    }

    private fun getPosts(
        data: Data.Fetch,
        user: Data.User
    ): Flow<PagingData<PostEntity>> {
        return repository.getPosts(data.query, data.sorting)
            .map { pagingData ->
                PostUtil.filterPosts(pagingData, latestUser ?: user, postMapper, defaultDispatcher)
            }
    }

    fun loadSubredditInfo(forceUpdate: Boolean) {
        if (_subreddit.value.isNotBlank()) {
            if (_about.value !is Resource.Success || forceUpdate) {
                loadSubredditInfo(_subreddit.value)
            }
        } else {
            _about.value = Resource.Error()
        }
    }

    private fun loadSubredditInfo(subreddit: String) {
        viewModelScope.launch {
            repository.getSubredditInfo(subreddit)
                .onStart {
                    _about.value = Resource.Loading()
                }
                .catch {
                    when (it) {
                        is IOException -> _about.value = Resource.Error(message = it.message)
                        is HttpException -> _about.value = Resource.Error(it.code(), it.message())
                        else -> _about.value = Resource.Error()
                    }
                }
                .map {
                    subredditMapper.dataToEntity(it.data)
                }
                .collect {
                    _about.value = Resource.Success(it)
                }
        }
    }

    fun setSubreddit(subreddit: String) {
        _subreddit.updateValue(subreddit)
    }

    fun setSorting(sorting: Sorting) {
        _sorting.updateValue(sorting)
    }

    fun setPostLayout(layout: PostLayout) {
        viewModelScope.launch { preferencesRepository.setPostLayout(subreddit.value, layout) }
    }

    fun toggleDescriptionCollapsed() {
        _isDescriptionCollapsed.value = !_isDescriptionCollapsed.value
    }

    fun toggleSubscription() {
        viewModelScope.launch {
            currentProfile.latest?.let {
                if (isSubscribed.value) {
                    repository.unsubscribe(subredditName, it.id)
                } else {
                    repository.subscribe(subredditName, it.id, icon)
                }
            }
        }
    }

    // ponytail: picker asks for a one-shot snapshot instead of exposing a live multireddits
    // flow to the fragment - the dialog doesn't need to stay in sync while it's open.
    suspend fun getMultiredditsSnapshot(): List<MultiredditWithMembers> {
        val profileId = currentProfile.latest?.id ?: return emptyList()
        return repository.getMultireddits(profileId).first()
    }

    fun addTargetToMultireddit(multiId: Long, target: String) {
        viewModelScope.launch { repository.addMember(multiId, target, MultiredditMemberType.SUBREDDIT) }
    }

    fun removeTargetFromMultireddit(multiId: Long, target: String) {
        viewModelScope.launch { repository.removeMember(multiId, target, MultiredditMemberType.SUBREDDIT) }
    }

    fun createMultiredditWithTarget(name: String, target: String) {
        viewModelScope.launch {
            currentProfile.latest?.let { profile ->
                val multiId = repository.createMultireddit(name, profile.id)
                repository.addMember(multiId, target, MultiredditMemberType.SUBREDDIT)
            }
        }
    }

    companion object {
        private val DEFAULT_SORTING = Sorting(Sort.HOT)
    }
}
