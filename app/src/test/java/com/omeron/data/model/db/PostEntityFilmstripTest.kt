package com.omeron.data.model.db

import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.PosterType
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostEntityFilmstripTest {

    private fun searchResult(
        preview: String?,
        isSelf: Boolean = false,
        url: String = ""
    ) = PostEntity(
        id = "t3_a",
        subreddit = "r/test",
        title = "Title",
        ratio = -1,
        totalAwards = 0,
        isOC = false,
        score = "1",
        type = PostType.LINK,
        domain = "",
        isSelf = isSelf,
        selfTextHtml = null,
        suggestedSorting = Sorting(Sort.BEST),
        isOver18 = false,
        preview = preview,
        isSpoiler = false,
        isArchived = false,
        isLocked = false,
        posterType = PosterType.REGULAR,
        author = "author",
        commentsNumber = "0",
        permalink = "/r/test/comments/a",
        isStickied = false,
        url = url,
        created = 0L,
        mediaType = MediaType.LINK,
        mediaUrl = url
    )

    @Test
    fun `result with a thumbnail image url is a candidate`() {
        assertTrue(searchResult("https://b.thumbs.redditmedia.com/x.jpg").isFilmstripSearchCandidate())
    }

    @Test
    fun `self result is never a candidate`() {
        assertFalse(searchResult("https://b.thumbs.redditmedia.com/x.jpg", isSelf = true).isFilmstripSearchCandidate())
    }

    @Test
    fun `missing or blank thumbnail is not a candidate`() {
        assertFalse(searchResult(null).isFilmstripSearchCandidate())
        assertFalse(searchResult("  ").isFilmstripSearchCandidate())
    }

    @Test
    fun `placeholder keywords are not candidates`() {
        listOf("self", "default", "nsfw", "spoiler", "image", "https:self", "NSFW").forEach { keyword ->
            assertFalse(keyword, searchResult(keyword).isFilmstripSearchCandidate())
        }
    }

    @Test
    fun `only a post without a link needs hydration`() {
        assertTrue(searchResult("https://t/x.jpg").needsHydration)
        assertFalse(searchResult("https://t/x.jpg", url = "https://i.redd.it/x.jpg").needsHydration)
    }
}
