package com.omeron.ui.filmstrip

import com.omeron.data.model.db.PostEntity
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Hand-off between a post list and the Filmstrip viewer. The list owns the paging data, so the
 * viewer never collects it a second time: the list publishes its loaded media posts here, and the
 * viewer asks the list for more through [loadMoreRequests]. Lives as long as the activity so it
 * survives rotation.
 *
 * Several sessions can be open at once: a subreddit opened from the home feed, or a viewer
 * opened on a single post from a post page that was itself opened from a viewer. Each session has
 * an id and its own posts, so a viewer covered by another one keeps its own list.
 */
@ActivityRetainedScoped
class FilmstripFeedHolder @Inject constructor() {

    /** The viewer closed; the list of [sessionId] should reveal the tile of [lastPostId]. */
    data class ViewerClosed(val sessionId: Int, val lastPostId: String?)

    private class Session(val posts: List<PostEntity>, val canLoadMore: Boolean)

    private val sessions = MutableStateFlow<Map<Int, Session>>(emptyMap())

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

    /**
     * Opens a session. A session with [canLoadMore] false has no list behind it (a single post),
     * so requests for more posts are not sent.
     */
    fun beginSession(initialPosts: List<PostEntity>, canLoadMore: Boolean = true): Int {
        val sessionId = nextSessionId++
        sessions.value = sessions.value + (sessionId to Session(initialPosts, canLoadMore))
        return sessionId
    }

    fun isOpen(sessionId: Int): Boolean = sessionId in sessions.value

    /** Posts of [sessionId]; empty once the session has ended. */
    fun postsOf(sessionId: Int): Flow<List<PostEntity>> {
        return sessions
            .map { openSessions -> openSessions[sessionId]?.posts.orEmpty() }
            .distinctUntilChanged()
    }

    fun currentPostsOf(sessionId: Int): List<PostEntity> {
        return sessions.value[sessionId]?.posts.orEmpty()
    }

    fun publish(sessionId: Int, posts: List<PostEntity>) {
        val session = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (sessionId to Session(posts, session.canLoadMore))
    }

    fun requestMore(sessionId: Int) {
        if (sessions.value[sessionId]?.canLoadMore == true) {
            _loadMoreRequests.tryEmit(sessionId)
        }
    }

    fun endSession(sessionId: Int, lastPostId: String?) {
        if (!isOpen(sessionId)) return
        sessions.value = sessions.value - sessionId
        _viewerClosed.tryEmit(ViewerClosed(sessionId, lastPostId))
    }
}
