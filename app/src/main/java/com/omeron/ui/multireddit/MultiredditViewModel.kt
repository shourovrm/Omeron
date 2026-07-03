package com.omeron.ui.multireddit

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omeron.data.local.mapper.PostMapper2
import com.omeron.data.model.Data
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.PostUtil
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

// A multireddit has no single subreddit, so postLayout reads/writes the global default -
// same reasoning as PostListViewModel (Home Feed).
@HiltViewModel
class MultiredditViewModel @Inject constructor(
    private val repository: PostListRepository,
    private val preferencesRepository: PreferencesRepository,
    private val postMapper: PostMapper2,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    private val _id: MutableStateFlow<Long?> = MutableStateFlow(null)

    private val _sorting: MutableStateFlow<Sorting> = MutableStateFlow(DEFAULT_SORTING)
    val sorting: StateFlow<Sorting> = _sorting

    val postLayout: Flow<PostLayout> = preferencesRepository.getPostLayout()

    val contentPreferences: Flow<ContentPreferences> =
        preferencesRepository.getContentPreferences()

    // Reactive on purpose: rename/member edits from the edit dialog must refresh this feed
    // page while it's open (e.g. appbar label, feed source) without navigating away.
    val multireddit: Flow<MultiredditWithMembers?> = _id.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            currentProfile.flatMapLatest { repository.getMultireddits(it.id) }
                .map { list -> list.find { it.multireddit.id == id } }
        }
    }

    private data class FeedFetch(val subreddits: List<String>, val users: List<String>, val sorting: Sorting)

    private val fetchData: Flow<FeedFetch> = combine(multireddit, sorting) { multi, sorting ->
        FeedFetch(
            subreddits = membersOf(multi, MultiredditMemberType.SUBREDDIT),
            users = membersOf(multi, MultiredditMemberType.USER),
            sorting = sorting
        )
    }

    private fun membersOf(multi: MultiredditWithMembers?, type: MultiredditMemberType): List<String> {
        return multi?.members
            ?.filter { MultiredditMemberType.fromValue(it.type) == type }
            ?.map { it.targetName }
            ?: emptyList()
    }

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

    val postDataFlow: Flow<PagingData<PostEntity>>

    init {
        postDataFlow = fetchData
            .flatMapLatest { fetchData -> userData.map { fetchData to it } }
            .flatMapLatest { getPosts(it.first, it.second) }
            .cachedIn(viewModelScope)
    }

    private fun getPosts(data: FeedFetch, user: Data.User): Flow<PagingData<PostEntity>> {
        return repository.getMultiredditPosts(data.subreddits, data.users, data.sorting)
            .map { pagingData ->
                PostUtil.filterPosts(pagingData, latestUser ?: user, postMapper, defaultDispatcher)
            }
    }

    fun load(id: Long) {
        _id.value = id
    }

    fun setSorting(sorting: Sorting) {
        _sorting.updateValue(sorting)
    }

    fun setPostLayout(layout: PostLayout) {
        viewModelScope.launch { preferencesRepository.setPostLayout(layout = layout) }
    }

    fun setHidden(hidden: Boolean) {
        val id = _id.value ?: return
        viewModelScope.launch { repository.setMultiredditHidden(id, hidden) }
    }

    fun delete() {
        val id = _id.value ?: return
        viewModelScope.launch { repository.deleteMultireddit(id) }
    }

    companion object {
        private val DEFAULT_SORTING = Sorting(Sort.HOT)
    }
}
