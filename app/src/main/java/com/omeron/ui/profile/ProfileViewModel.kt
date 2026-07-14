package com.omeron.ui.profile

import androidx.lifecycle.viewModelScope
import com.omeron.data.local.mapper.SavedMapper2
import com.omeron.data.model.Comment
import com.omeron.data.model.SavedItem
import com.omeron.data.model.db.History
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.Profile
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.extension.latest
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val repository: PostListRepository,
    private val savedMapper: SavedMapper2,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> = preferencesRepository.getContentPreferences()

    private val _page: MutableStateFlow<Int> = MutableStateFlow(0)
    val page: StateFlow<Int> get() = _page

    private val _searchQuery: MutableStateFlow<String> = MutableStateFlow("")
    val searchQuery: StateFlow<String> get() = _searchQuery

    var layoutState: Int? = null

    private val _savedPosts: Flow<List<PostEntity>> = currentProfile.flatMapLatest {
        repository.getSavedPosts(it.id)
    }

    private val _savedComments: Flow<List<Comment.CommentEntity>> = currentProfile.flatMapLatest {
        repository.getSavedComments(it.id)
    }

    private val _history: Flow<List<History>> = currentProfile.flatMapLatest {
        repository.getHistory(it.id)
    }

    val selectedProfile: Flow<Profile> = combine(
        currentProfile,
        repository.getAllProfiles()
    ) { currentProfile, profiles ->
        // Update current profile when any profile is updated
        profiles.find { it.id == currentProfile.id } ?: currentProfile
    }

    val savedPosts: Flow<List<SavedItem>> = combine(
        _savedPosts,
        contentPreferences,
        searchQuery
    ) { posts, preferences, query ->
        savedMapper.postsToEntities(posts).filter {
            preferences.showNsfw || !(it as SavedItem.Post).post.isOver18
        }.filterPosts(query)
    }.map { items ->
        items.sortedByDescending { it.timestamp }
    }.flowOn(defaultDispatcher)

    val savedComments: Flow<List<SavedItem>> = combine(
        _savedComments,
        searchQuery
    ) { comments, query ->
        savedMapper.commentsToEntities(comments).sortedByDescending { it.timestamp }.filterComments(query)
    }.flowOn(defaultDispatcher)

    val historyPosts: Flow<List<SavedItem>> = combine(
        _history,
        savedPostIds,
        contentPreferences,
        searchQuery
    ) { history, saved, preferences, query ->
        savedMapper.historyToEntities(history, saved).filter {
            preferences.showNsfw || !(it as SavedItem.Post).post.isOver18
        }.filterPosts(query)
    }.flowOn(defaultDispatcher)

    fun setPage(position: Int) {
        _page.updateValue(position)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.updateValue(query)
    }

    // ponytail: saved/history lists are already fully loaded in memory (Room Flow<List<..>>,
    // not paged), so a plain in-memory filter combined into the existing flows is the smallest
    // diff - matches SubscriptionsViewModel.filteredSubscriptions instead of new DAO LIKE queries.
    private fun List<SavedItem>.filterPosts(query: String): List<SavedItem> {
        if (query.isBlank()) return this
        return filter {
            val post = (it as SavedItem.Post).post
            post.title.contains(query, ignoreCase = true) ||
                post.subreddit.contains(query, ignoreCase = true) ||
                post.author.contains(query, ignoreCase = true)
        }
    }

    private fun List<SavedItem>.filterComments(query: String): List<SavedItem> {
        if (query.isBlank()) return this
        return filter {
            val comment = (it as SavedItem.Comment).comment
            comment.bodyHtml.contains(query, ignoreCase = true) ||
                comment.subreddit.contains(query, ignoreCase = true) ||
                comment.author.contains(query, ignoreCase = true)
        }
    }

    fun removeFromHistory(postId: String) {
        viewModelScope.launch {
            currentProfile.latest?.let {
                repository.deletePostFromHistory(postId, it.id)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            currentProfile.latest?.let {
                repository.clearHistory(it.id)
            }
        }
    }
}
