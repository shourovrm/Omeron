package com.omeron.ui.postlist

import com.omeron.ui.postlist.PostListViewModel.Companion.resolveSubreddit
import com.omeron.ui.postlist.PostListViewModel.FeedMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves FeedMode -> subreddit resolution: POPULAR always reads r/popular, HOME reads
 * subscriptions and only falls back to r/popular when there are none.
 */
class PostListViewModelTest {

    @Test
    fun `popular mode ignores subscriptions`() {
        assertEquals(listOf("popular"), resolveSubreddit(FeedMode.POPULAR, listOf("android", "kotlin")))
        assertEquals(listOf("popular"), resolveSubreddit(FeedMode.POPULAR, emptyList()))
    }

    @Test
    fun `home mode returns subscriptions when present`() {
        assertEquals(
            listOf("android", "kotlin"),
            resolveSubreddit(FeedMode.HOME, listOf("android", "kotlin"))
        )
    }

    @Test
    fun `home mode falls back to popular when no subscriptions`() {
        assertEquals(listOf("popular"), resolveSubreddit(FeedMode.HOME, emptyList()))
    }
}
