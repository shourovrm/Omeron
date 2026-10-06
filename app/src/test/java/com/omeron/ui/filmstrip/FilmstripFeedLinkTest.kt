package com.omeron.ui.filmstrip

import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.PosterType
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.PostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmstripFeedLinkTest {

    private fun post(id: String, type: PostType) = PostEntity(
        id = id,
        subreddit = "r/test",
        title = "Title $id",
        ratio = 100,
        totalAwards = 0,
        isOC = false,
        score = "1",
        type = type,
        domain = "i.redd.it",
        isSelf = type == PostType.TEXT,
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

    private val feed = listOf(
        post("text", PostType.TEXT),
        post("image", PostType.IMAGE),
        post("link", PostType.LINK),
        post("video", PostType.VIDEO)
    )

    @Test
    fun `published posts keep feed order and skip posts that are not media`() {
        val published = FilmstripFeedLink.mediaPosts(feed, PostEntity::hasFilmstripMedia)

        assertEquals(listOf("image", "video"), published.map { it.id })
    }

    @Test
    fun `placeholders are skipped when publishing`() {
        val published = FilmstripFeedLink.mediaPosts(
            listOf(null, feed[1]),
            PostEntity::hasFilmstripMedia
        )

        assertEquals(listOf("image"), published.map { it.id })
    }

    @Test
    fun `filtering an already filtered list changes nothing`() {
        val once = FilmstripFeedLink.mediaPosts(feed, PostEntity::hasFilmstripMedia)
        val twice = FilmstripFeedLink.mediaPosts(once, PostEntity::hasFilmstripMedia)

        assertEquals(once, twice)
    }

    @Test
    fun `adapter position counts the posts left out of the published list`() {
        val publishedIndex = FilmstripFeedLink.mediaPosts(feed, PostEntity::hasFilmstripMedia)
            .indexOfFirst { it.id == "video" }

        assertEquals(1, publishedIndex)
        assertEquals(3, FilmstripFeedLink.adapterPositionOfPost(feed, "video"))
    }

    @Test
    fun `adapter position of an unknown or null post is negative`() {
        assertEquals(-1, FilmstripFeedLink.adapterPositionOfPost(feed, "missing"))
        assertEquals(-1, FilmstripFeedLink.adapterPositionOfPost(feed, null))
        assertEquals(-1, FilmstripFeedLink.adapterPositionOfPost(listOf(null), null))
    }

    @Test
    fun `loading continues while a page adds no media`() {
        assertTrue(FilmstripFeedLink.shouldKeepLoading(5, 5, isFeedExhausted = false))
    }

    @Test
    fun `loading stops once media arrives`() {
        assertFalse(FilmstripFeedLink.shouldKeepLoading(5, 7, isFeedExhausted = false))
    }

    @Test
    fun `loading stops when the feed is exhausted`() {
        assertFalse(FilmstripFeedLink.shouldKeepLoading(5, 5, isFeedExhausted = true))
    }
}
