package com.omeron.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omeron.data.model.Comment
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.Profile
import com.omeron.data.model.db.Subscription
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

open class BaseViewModel(
    preferencesRepository: PreferencesRepository,
    private val postListRepository: PostListRepository
) : ViewModel() {

    val currentProfile: SharedFlow<Profile> = preferencesRepository.getCurrentProfile().map {
        postListRepository.getProfile(it)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    protected val historyIds: Flow<List<String>> = currentProfile.flatMapLatest {
        postListRepository.getHistoryIds(it.id)
    }

    protected val subscriptions: Flow<List<Subscription>> = currentProfile.flatMapLatest {
        postListRepository.getSubscriptions(it.id)
    }

    protected val subscriptionsNames: Flow<List<String>> = currentProfile.flatMapLatest {
        postListRepository.getSubscriptionsNames(it.id)
    }

    protected val savedPostIds: Flow<List<String>> = currentProfile.flatMapLatest {
        postListRepository.getSavedPostIds(it.id)
    }

    // Awaits the profile for the same reason as toggleSavePost: an empty replay cache dropped the
    // write on screens that never collect currentProfile.
    fun insertPostInHistory(post: PostEntity) {
        viewModelScope.launch {
            postListRepository.insertPostInHistory(post, currentProfile.first().id)
        }
    }

    // Callers flip post.saved right after this call, so the state is read before anything suspends.
    // The profile is awaited rather than read from the replay cache: currentProfile only shares
    // while something collects it, and a screen like the filmstrip viewer never does, so the cache
    // there is empty and the save used to be dropped silently.
    fun toggleSavePost(post: PostEntity) {
        val wasSaved = post.saved
        viewModelScope.launch {
            val profile = currentProfile.first()
            if (wasSaved) {
                postListRepository.unsavePost(post, profile.id)
            } else {
                postListRepository.savePost(post, profile.id)
            }
        }
    }

    // Same ordering and profile rule as toggleSavePost.
    fun toggleSaveComment(comment: Comment.CommentEntity) {
        val wasSaved = comment.saved
        viewModelScope.launch {
            val profile = currentProfile.first()
            if (wasSaved) {
                postListRepository.unsaveComment(comment, profile.id)
            } else {
                postListRepository.saveComment(comment, profile.id)
            }
        }
    }
}
