package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.PostChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the search-results HTML parse logic used by RedditScrapingSource.searchPost()
 * without needing a device/network. Users aren't covered here: old.reddit's
 * /search?type=user renders no result rows at all (confirmed against a live sample), so
 * there's nothing to scrape and no UserSearchScraper anymore.
 */
class SearchScraperTest {

    @Test
    fun `PostScraper extracts posts from listing type=link result markup`() = runBlocking {
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
    fun `PostSearchScraper extracts posts from search type=link result markup`() = runBlocking {
        // Trimmed from a real old.reddit /search?type=link response.
        val html = """
            <div class="listing search-result-listing"><div class="search-result-group"><div class="contents">
            <div class=" search-result search-result-link has-thumbnail no-linkflair " data-fullname="t3_1u4mqlm">
                <a href="/r/phones/comments/1u4mqlm/android_users_who_switched_to_iphone_was_it_worth/" class="may-blank thumbnail self"></a>
                <div>
                    <header class="search-result-header"><a href="https://old.reddit.com/r/phones/comments/1u4mqlm/android_users_who_switched_to_iphone_was_it_worth/" class="search-title may-blank">Android users who switched to iPhone, was it worth it?</a></header>
                    <div class="search-result-meta">
                        <span class="search-score">40 points</span>
                        <a href="#" class="search-comments may-blank">138 comments</a>
                        <span class="search-time">submitted <time title="Sat Jun 13 09:47:04 2026 UTC" datetime="2026-06-13T09:47:04+00:00">20 days ago</time></span>
                        <span class="search-author">by <a href="https://old.reddit.com/user/OneReaction9115" class="author may-blank id-t2_2g94ewz24q">OneReaction9115</a></span>
                        <span>to <a href="https://old.reddit.com/r/phones/" class="search-subreddit-link may-blank">r/phones</a></span>
                    </div>
                </div>
            </div>
            <div class=" search-result search-result-link has-thumbnail has-linkflair linkflair-flair-discussion " data-fullname="t3_1rbo8g0">
                <a href="/r/degoogle/comments/1rbo8g0/android_will_become_a_locked_down_platform/" class="may-blank thumbnail ">
                    <img src="//external-preview.redd.it/example.png?width=140" width="70" height="70" alt="">
                </a>
                <div>
                    <header class="search-result-header"><a href="https://old.reddit.com/r/degoogle/comments/1rbo8g0/android_will_become_a_locked_down_platform/" class="search-title may-blank">Android will become a locked down platform in 190 days</a><span class="linkflairlabel " title="Discussion">Discussion</span></header>
                    <div class="search-result-meta">
                        <span class="search-score">4,881 points</span>
                        <a href="#" class="search-comments may-blank">512 comments</a>
                        <span class="search-time">submitted <time title="Mon Jan 1 00:00:00 2026 UTC" datetime="2026-01-01T00:00:00+00:00">6 months ago</time></span>
                        <span class="search-author">by <a href="https://old.reddit.com/user/nbatman" class="author moderator may-blank id-t2_tf4s0">nbatman</a></span>
                        <span>to <a href="https://old.reddit.com/r/degoogle/" class="search-subreddit-link may-blank">r/degoogle</a></span>
                    </div>
                </div>
            </div>
            </div></div></div>
        """.trimIndent()

        val listing = PostSearchScraper(Dispatchers.Unconfined).scrap(html)

        assertEquals(2, listing.data.children.size)

        val first = (listing.data.children[0] as PostChild).data
        assertEquals("t3_1u4mqlm", first.name)
        assertEquals("Android users who switched to iPhone, was it worth it?", first.title)
        assertEquals("phones", first.subreddit)
        assertEquals(40, first.score)
        assertEquals(138, first.commentsNumber)
        assertEquals("OneReaction9115", first.author)
        assertEquals(
            "/r/phones/comments/1u4mqlm/android_users_who_switched_to_iphone_was_it_worth/",
            first.permalink
        )
        assertEquals(true, first.isSelf)

        val second = (listing.data.children[1] as PostChild).data
        assertEquals("degoogle", second.subreddit)
        assertEquals(4881, second.score)
        assertEquals(512, second.commentsNumber)
        assertEquals("Discussion", second.flair)
        assertEquals("moderator", second.distinguished)
        assertEquals(false, second.isSelf)
    }
}
