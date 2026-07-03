package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.AboutUserChild
import com.omeron.data.remote.api.reddit.model.PostChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the search-results HTML parse logic used by RedditScrapingSource.searchPost()
 * and .searchUser() without needing a device/network.
 */
class SearchScraperTest {

    @Test
    fun `PostScraper extracts posts from search type=link result markup`() = runBlocking {
        val html = """
            <div class="thing" id="thing_t3_abc123"
                data-fullname="t3_abc123"
                data-subreddit="AskReddit"
                data-subreddit-prefixed="r/AskReddit"
                data-domain="self.AskReddit"
                data-score="42"
                data-comments-count="7"
                data-permalink="/r/AskReddit/comments/abc123/some_title/"
                data-url="https://old.reddit.com/r/AskReddit/comments/abc123/some_title/"
                data-timestamp="1700000000000"
                data-oc="false"
                data-nsfw="false"
                data-spoiler="false">
                <p class="title"><a class="title" href="#">Some search result title</a></p>
            </div>
        """.trimIndent()

        val listing = PostScraper(Dispatchers.Unconfined).scrap(html)

        assertEquals(1, listing.data.children.size)
        val post = listing.data.children.first() as PostChild
        assertEquals("Some search result title", post.data.title)
        assertEquals("AskReddit", post.data.subreddit)
        assertEquals(42, post.data.score)
    }

    @Test
    fun `UserSearchScraper extracts users from search type=user result markup`() = runBlocking {
        val html = """
            <div class="search-result search-result-user">
                <a class="author" href="https://old.reddit.com/user/some_user/">some_user</a>
                <span class="karma">1,234</span>
            </div>
        """.trimIndent()

        val listing = UserSearchScraper(Dispatchers.Unconfined).scrap(html)

        assertEquals(1, listing.data.children.size)
        val user = listing.data.children.first() as AboutUserChild
        assertEquals("some_user", user.data.name)
        assertEquals(1234, user.data.linkKarma)
    }
}
