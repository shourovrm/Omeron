package com.omeron.ui.user

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.omeron.data.local.mapper.CommentMapper2
import com.omeron.data.local.mapper.PostMapper2
import com.omeron.data.local.mapper.UserMapper2
import com.omeron.data.model.Comment
import com.omeron.data.model.Data
import com.omeron.data.model.Resource
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.User
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: PostListRepository,
    private val preferencesRepository: PreferencesRepository,
    private val postMapper: PostMapper2,
    private val commentMapper: CommentMapper2,
    private val userMapper: UserMapper2,
    @DispatchersModule.DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> =
        preferencesRepository.getContentPreferences()

    // User post listings aren't scoped to one subreddit, so this is always the global default.
    val postLayout: Flow<PostLayout> = preferencesRepository.getPostLayout()

    private val _sorting: MutableStateFlow<Sorting> = MutableStateFlow(DEFAULT_SORTING)
    val sorting: StateFlow<Sorting> = _sorting

    private val _user: MutableStateFlow<String> = MutableStateFlow("")
    val user: StateFlow<String> = _user

    private val _page: MutableStateFlow<Int> = MutableStateFlow(0)
    val page: StateFlow<Int> get() = _page

    private val _about: MutableStateFlow<Resource<User>> = MutableStateFlow(Resource.Loading())
    val about: StateFlow<Resource<User>> = _about

    var layoutState: Int? = null

    val isFollowed: Flow<Boolean> = user.flatMapLatest { name ->
        if (name.isBlank()) {
            flowOf(false)
        } else {
            currentProfile.flatMapLatest { repository.isUserFollowed(name, it.id) }
        }
    }

    private val savedCommentIds: Flow<List<String>> = currentProfile.flatMapLatest {
        repository.getSavedCommentIds(it.id)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private val _lastRefreshPost: MutableStateFlow<Long> =
        MutableStateFlow(System.currentTimeMillis())
    val lastRefreshPost: StateFlow<Long> = _lastRefreshPost.asStateFlow()

    private val _lastRefreshComment: MutableStateFlow<Long> =
        MutableStateFlow(System.currentTimeMillis())
    val lastRefreshComment: StateFlow<Long> = _lastRefreshComment.asStateFlow()

    val postDataFlow: Flow<PagingData<PostEntity>>
    val commentDataFlow: Flow<PagingData<Comment>>

    private val searchData: StateFlow<Data.Fetch> = combine(
        user,
        sorting
    ) { user, sorting ->
        Data.Fetch(user, sorting)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Data.Fetch("", DEFAULT_SORTING)
    )

    private var latestUser: Data.User? = null

    private val userData: Flow<Data.User> = combine(
        historyIds,
        savedPostIds,
        contentPreferences,
        savedCommentIds
    ) { history, saved, prefs, savedComments ->
        Data.User(history, saved, prefs, savedComments)
    }.onEach {
        latestUser = it
    }.distinctUntilChangedBy {
        it.contentPreferences
    }

    val data: Flow<Pair<Data.Fetch, Data.User>> = searchData
        .dropWhile { it.query.isBlank() }
        .flatMapLatest { searchData -> userData.map { searchData to it } }

    init {
        postDataFlow = data
            .flatMapLatest { data -> getPosts(data.first, data.second) }
            .onEach { _lastRefreshPost.value = System.currentTimeMillis() }
            .cachedIn(viewModelScope)

        commentDataFlow = data
            .flatMapLatest { data -> getComments(data.first, data.second) }
            .onEach { _lastRefreshComment.value = System.currentTimeMillis() }
            .cachedIn(viewModelScope)
    }

    private fun getPosts(
        data: Data.Fetch,
        user: Data.User
    ): Flow<PagingData<PostEntity>> {
        return repository.getUserPosts(data.query, data.sorting)
            .map { pagingData ->
                PostUtil.filterPosts(pagingData, latestUser ?: user, postMapper, defaultDispatcher)
            }
    }

    private fun getComments(
        data: Data.Fetch,
        user: Data.User
    ): Flow<PagingData<Comment>> {
        return repository.getUserComments(data.query, data.sorting)
            .map { pagingData ->
                pagingData
                    .map { commentMapper.dataToEntity(it, null) }
                    .map { comment ->
                        comment.apply {
                            (this as? Comment.CommentEntity)?.saved =
                                (latestUser ?: user).savedComments?.contains(this.name) ?: false
                        }
                    }
            }
            .flowOn(defaultDispatcher)
    }

    fun loadUserInfo(forceUpdate: Boolean) {
        if (_user.value.isNotBlank()) {
            if (_about.value !is Resource.Success || forceUpdate) {
                loadUserInfo(_user.value)
            }
        } else {
            _about.value = Resource.Error()
        }
    }

    private fun loadUserInfo(user: String) {
        viewModelScope.launch {
            repository.getUserInfo(user).onStart {
                _about.value = Resource.Loading()
            }.catch {
                when (it) {
                    is IOException -> _about.value = Resource.Error(message = it.message)
                    is HttpException -> _about.value = Resource.Error(it.code(), it.message())
                    else -> _about.value = Resource.Error()
                }
            }.map {
                userMapper.dataToEntity(it.data)
            }.collect {
                _about.value = Resource.Success(it)
            }
        }
    }

    fun setSorting(sorting: Sorting) {
        _sorting.updateValue(sorting)
    }

    fun setPostLayout(layout: PostLayout) {
        viewModelScope.launch { preferencesRepository.setPostLayout(layout = layout) }
    }

    fun setUser(user: String) {
        _user.updateValue(user)
    }

    fun toggleFollow() {
        val name = _user.value
        if (name.isBlank()) return

        viewModelScope.launch {
            currentProfile.latest?.let { profile ->
                if (repository.isUserFollowed(name, profile.id).first()) {
                    repository.unfollowUser(name, profile.id)
                } else {
                    val icon = (about.value as? Resource.Success)?.data?.icon
                    repository.followUser(name, profile.id, icon)
                }
            }
        }
    }

    fun setPage(position: Int) {
        _page.updateValue(position)
    }

    companion object {
        private val DEFAULT_SORTING = Sorting(Sort.NEW)
    }
}
