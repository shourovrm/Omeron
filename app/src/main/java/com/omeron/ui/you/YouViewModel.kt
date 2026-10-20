package com.omeron.ui.you

import com.omeron.data.model.db.Profile
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class YouViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val repository: PostListRepository
) : BaseViewModel(preferencesRepository, repository) {

    // Same rule as the Reddit account row in Settings: a stored session cookie means logged in.
    val isRedditLoggedIn: Flow<Boolean> = preferencesRepository
        .getRedditCookies()
        .map { it.isNotBlank() }

    // Picks up a rename made in the profile manager, which changes the stored row but not its id.
    val selectedProfile: Flow<Profile> = combine(
        currentProfile,
        repository.getAllProfiles()
    ) { currentProfile, profiles ->
        profiles.find { it.id == currentProfile.id } ?: currentProfile
    }

    // The profile lists hide NSFW posts when that setting is off, so the counts do too; otherwise
    // a row could promise more posts than its list shows.
    private val showNsfw: Flow<Boolean> = preferencesRepository
        .getContentPreferences()
        .map { it.showNsfw }

    val savedPostsCount: Flow<Int> = combine(
        currentProfile.flatMapLatest { profile -> repository.getSavedPosts(profile.id) },
        showNsfw
    ) { posts, showNsfw ->
        posts.count { showNsfw || !it.isOver18 }
    }

    val savedCommentsCount: Flow<Int> = currentProfile.flatMapLatest { profile ->
        repository.getSavedComments(profile.id)
    }.map { it.size }

    val historyCount: Flow<Int> = combine(
        currentProfile.flatMapLatest { profile -> repository.getHistory(profile.id) },
        showNsfw
    ) { history, showNsfw ->
        history.count { showNsfw || !it.isOver18 }
    }
}
