package com.omeron.ui.profile

import com.omeron.data.local.mapper.SavedMapper2
import com.omeron.data.model.Comment
import com.omeron.data.model.SavedItem
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.Profile
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.ui.base.BaseViewModel
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
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    repository: PostListRepository,
    private val savedMapper: SavedMapper2,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> = preferencesRepository.getContentPreferences()

    private val _page: MutableStateFlow<Int> = MutableStateFlow(0)
    val page: StateFlow<Int> get() = _page

    var layoutState: Int? = null

    private val _savedPosts: Flow<List<PostEntity>> = currentProfile.flatMapLatest {
        repository.getSavedPosts(it.id)
    }

    private val _savedComments: Flow<List<Comment.CommentEntity>> = currentProfile.flatMapLatest {
        repository.getSavedComments(it.id)
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
        contentPreferences
    ) { posts, preferences ->
        savedMapper.postsToEntities(posts).filter {
            preferences.showNsfw || !(it as SavedItem.Post).post.isOver18
        }
    }.map { items ->
        items.sortedByDescending { it.timestamp }
    }.flowOn(defaultDispatcher)

    val savedComments: Flow<List<SavedItem>> = _savedComments.map { comments ->
        savedMapper.commentsToEntities(comments).sortedByDescending { it.timestamp }
    }.flowOn(defaultDispatcher)

    fun setPage(position: Int) {
        _page.updateValue(position)
    }
}
