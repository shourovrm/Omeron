package com.omeron.ui.filmstrip

import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.PosterType
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.PostEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmstripFeedHolderTest {

    private fun post(id: String) = PostEntity(
        id = id,
        subreddit = "r/test",
        title = "Title $id",
        ratio = 100,
        totalAwards = 0,
        isOC = false,
        score = "1",
        type = PostType.IMAGE,
        domain = "i.redd.it",
        isSelf = false,
        selfTextHtml = null,
        suggestedSorting = Sorting(Sort.BEST),
        isOver18 = false,
        preview = "https://preview/$id.jpg",
        isSpoiler = false,
        isArchived = false,
        isLocked = false,
        posterType = PosterType.REGULAR,
        author = "author",
        commentsNumber = "0",
        permalink = "/r/test/comments/$id",
        isStickied = false,
        url = "https://example.com/$id",
        created = 0L,
        mediaType = MediaType.IMAGE,
        mediaUrl = "https://media/$id",
        gallery = emptyList()
    )

    @Test
    fun `sessions keep their own posts`() = runBlocking {
        val holder = FilmstripFeedHolder()
        val feedSession = holder.beginSession(listOf(post("a"), post("b")))
        val singlePostSession = holder.beginSession(listOf(post("c")), canLoadMore = false)

        assertEquals(listOf("a", "b"), holder.postsOf(feedSession).first().map { it.id })
        assertEquals(listOf("c"), holder.postsOf(singlePostSession).first().map { it.id })
    }

    @Test
    fun `ending one session leaves the other open`() = runBlocking {
        val holder = FilmstripFeedHolder()
        val feedSession = holder.beginSession(listOf(post("a")))
        val singlePostSession = holder.beginSession(listOf(post("c")), canLoadMore = false)

        holder.endSession(singlePostSession, lastPostId = "c")

        assertFalse(holder.isOpen(singlePostSession))
        assertTrue(holder.isOpen(feedSession))
        assertEquals(listOf("a"), holder.postsOf(feedSession).first().map { it.id })
        assertTrue(holder.postsOf(singlePostSession).first().isEmpty())
    }

    @Test
    fun `publishing to an ended session is ignored`() {
        val holder = FilmstripFeedHolder()
        val sessionId = holder.beginSession(listOf(post("a")))
        holder.endSession(sessionId, lastPostId = null)

        holder.publish(sessionId, listOf(post("a"), post("b")))

        assertFalse(holder.isOpen(sessionId))
        assertTrue(holder.currentPostsOf(sessionId).isEmpty())
    }

    @Test
    fun `publishing replaces the posts of an open session`() {
        val holder = FilmstripFeedHolder()
        val sessionId = holder.beginSession(listOf(post("a")), canLoadMore = false)

        holder.publish(sessionId, listOf(post("a"), post("b")))

        assertEquals(listOf("a", "b"), holder.currentPostsOf(sessionId).map { it.id })
    }

    @Test
    fun `a session with a list behind it forwards requests for more`() = runBlocking {
        val holder = FilmstripFeedHolder()
        val sessionId = holder.beginSession(listOf(post("a")))

        val requested = requestedSessionAfter(holder) { holder.requestMore(sessionId) }

        assertEquals(sessionId, requested)
    }

    @Test
    fun `a single post session sends no requests for more`() = runBlocking {
        val holder = FilmstripFeedHolder()
        val sessionId = holder.beginSession(listOf(post("a")), canLoadMore = false)

        val requested = requestedSessionAfter(holder) { holder.requestMore(sessionId) }

        assertNull(requested)
    }

    // The request flow has no replay, so the collector has to be running before the request.
    private suspend fun CoroutineScope.requestedSessionAfter(
        holder: FilmstripFeedHolder,
        request: () -> Unit
    ): Int? {
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(REQUEST_WAIT_MILLIS) { holder.loadMoreRequests.first() }
        }
        request()
        return pending.await()
    }

    private companion object {
        const val REQUEST_WAIT_MILLIS = 200L
    }
}
