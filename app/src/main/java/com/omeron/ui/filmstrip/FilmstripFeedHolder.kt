package com.omeron.ui.filmstrip

import com.omeron.data.model.db.PostEntity
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Hand-off between a feed list and the Filmstrip viewer. The list owns the paging data, so the
 * viewer never collects it a second time: the list publishes its loaded media posts here, and the
 * viewer asks the list for more through [loadMoreRequests]. Lives as long as the activity so it
 * survives rotation.
 *
 * Several lists can be alive at once (a subreddit opened from the home feed), so every list
 * session gets an id and only the active session may publish or react.
 */
@ActivityRetainedScoped
class FilmstripFeedHolder @Inject constructor() {

    /** The viewer closed; the list of [sessionId] should reveal the tile of [lastPostId]. */
    data class ViewerClosed(val sessionId: Int, val lastPostId: String?)

    private val _posts = MutableStateFlow<List<PostEntity>>(emptyList())
    val posts: StateFlow<List<PostEntity>> = _posts

    private val _loadMoreRequests = MutableSharedFlow<Int>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emits the id of the session that should load another page. */
    val loadMoreRequests: SharedFlow<Int> = _loadMoreRequests

    private val _viewerClosed = MutableSharedFlow<ViewerClosed>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val viewerClosed: SharedFlow<ViewerClosed> = _viewerClosed

    private var nextSessionId = 1

    var activeSessionId: Int? = null
        private set

    fun beginSession(initialPosts: List<PostEntity>): Int {
        val sessionId = nextSessionId++
        activeSessionId = sessionId
        _posts.value = initialPosts
        return sessionId
    }

    fun publish(sessionId: Int, posts: List<PostEntity>) {
        if (sessionId == activeSessionId) {
            _posts.value = posts
        }
    }

    fun requestMore() {
        activeSessionId?.let { _loadMoreRequests.tryEmit(it) }
    }

    fun endSession(lastPostId: String?) {
        val endedSessionId = activeSessionId ?: return
        activeSessionId = null
        _posts.value = emptyList()
        _viewerClosed.tryEmit(ViewerClosed(endedSessionId, lastPostId))
    }
}
