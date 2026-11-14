package com.omeron.data.remote.datasource

import com.omeron.data.remote.api.reddit.model.PostChild
import com.omeron.data.remote.api.reddit.model.PostData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPostListDataSourceTest {

    @Test
    fun `first page is returned unchanged`() {
        val page = listOf(post("t3_a"), post("t3_b"))

        assertEquals(page, page.withoutReturned(mutableSetOf()))
    }

    @Test
    fun `later page drops posts already returned`() {
        val returned = mutableSetOf<String>()
        listOf(post("t3_a"), post("t3_b")).withoutReturned(returned)
        val later = listOf(post("t3_b"), post("t3_c"))

        assertEquals(listOf(later[1]), later.withoutReturned(returned))
    }

    @Test
    fun `page with nothing new ends paging`() {
        val page = listOf(post("t3_a"), post("t3_b"))
        val returned = mutableSetOf<String>()
        page.withoutReturned(returned)

        // load() sets nextKey = null when a later page is empty after this filter
        assertTrue(page.withoutReturned(returned).isEmpty())
    }

    private fun post(name: String) = PostChild(
        PostData(
            subreddit = "popular",
            linkFlairRichText = emptyList(),
            authorFlairRichText = null,
            title = "title",
            prefixedSubreddit = "r/popular",
            name = name,
            ratio = null,
            totalAwards = 0,
            isOC = false,
            flair = null,
            authorFlair = null,
            galleryData = null,
            score = 0,
            hint = null,
            isSelf = true,
            crossposts = null,
            domain = "",
            selfTextHtml = null,
            suggestedSort = null,
            isArchived = false,
            isOver18 = false,
            mediaPreview = null,
            awardings = emptyList(),
            isSpoiler = false,
            isLocked = false,
            distinguished = null,
            author = "author",
            commentsNumber = 0,
            permalink = "/r/popular/comments/$name/",
            isStickied = false,
            url = "",
            created = 0,
            media = null,
            mediaMetadata = null,
            isRedditGallery = null,
            isVideo = false
        )
    )
}
