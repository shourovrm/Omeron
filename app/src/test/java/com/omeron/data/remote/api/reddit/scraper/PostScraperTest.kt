package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.PostChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PostScraperTest {

    // Trimmed from a real old.reddit listing: the expando's cached markup links the original file
    // and shows a signed rendition of it.
    private fun imagePost(expandoMarkup: String) = """
        <div class="thing" id="thing_t3_abc123"
            data-fullname="t3_abc123"
            data-subreddit="pics"
            data-subreddit-prefixed="r/pics"
            data-domain="i.redd.it"
            data-score="42"
            data-comments-count="7"
            data-permalink="/r/pics/comments/abc123/some_title/"
            data-url="https://i.redd.it/455juujtph1i1.jpeg"
            data-timestamp="1700000000000"
            data-oc="false"
            data-nsfw="false"
            data-spoiler="false">
            <p class="title"><a class="title" href="#">Some title</a></p>
            <div class="expando" data-cachedhtml="$expandoMarkup"></div>
        </div>
    """.trimIndent()

    @Test
    fun `list preview is the signed rendition, not the original file`() = runBlocking {
        val html = imagePost(
            "&lt;div class=&quot;media-preview&quot;&gt;&lt;div class=&quot;media-preview-content&quot;&gt;" +
                "&lt;a href=&quot;https://i.redd.it/455juujtph1i1.jpeg&quot;&gt;" +
                "&lt;img class=&quot;preview&quot; " +
                "src=&quot;https://preview.redd.it/455juujtph1i1.jpeg?width=1024&amp;amp;auto=webp&amp;amp;s=c28f&quot; " +
                "width=&quot;1024&quot; height=&quot;720&quot;&gt;&lt;/a&gt;&lt;/div&gt;&lt;/div&gt;"
        )

        val post = PostScraper(Dispatchers.Unconfined).scrap(html).data.children.first() as PostChild

        assertEquals(
            "https://preview.redd.it/455juujtph1i1.jpeg?width=1024&auto=webp&s=c28f",
            post.data.previewUrl
        )
        assertEquals("https://i.redd.it/455juujtph1i1.jpeg", post.data.url)
    }

    @Test
    fun `list preview of a gallery skips the small tiles`() = runBlocking {
        val html = imagePost(
            "&lt;div class=&quot;gallery-tile&quot;&gt;" +
                "&lt;div class=&quot;media-preview-content gallery-tile-content&quot;&gt;" +
                "&lt;img class=&quot;preview&quot; src=&quot;https://preview.redd.it/a.jpeg?width=108&quot;&gt;" +
                "&lt;/div&gt;&lt;/div&gt;" +
                "&lt;div class=&quot;gallery-preview&quot;&gt;&lt;div class=&quot;media-preview-content&quot;&gt;" +
                "&lt;a href=&quot;https://preview.redd.it/a.jpeg?width=4080&quot;&gt;" +
                "&lt;img class=&quot;preview&quot; src=&quot;https://preview.redd.it/a.jpeg?width=960&quot;&gt;" +
                "&lt;/a&gt;&lt;/div&gt;&lt;/div&gt;"
        )

        val post = PostScraper(Dispatchers.Unconfined).scrap(html).data.children.first() as PostChild

        assertEquals("https://preview.redd.it/a.jpeg?width=960", post.data.previewUrl)
    }

    @Test
    fun `gallery picture takes its thumbnail from the tile with the same media id`() = runBlocking {
        val html = imagePost(
            "&lt;div class=&quot;gallery-tile&quot; id=&quot;media-tile-p1-m1&quot; data-media-id=&quot;m1&quot;&gt;" +
                "&lt;div class=&quot;media-preview-content gallery-tile-content&quot;&gt;" +
                "&lt;img class=&quot;preview&quot; src=&quot;https://preview.redd.it/m1.gif?width=108&quot;&gt;" +
                "&lt;/div&gt;&lt;/div&gt;" +
                "&lt;div class=&quot;gallery-preview&quot; id=&quot;gallery-preview-p1-m1&quot;&gt;" +
                "&lt;div class=&quot;media-preview-content&quot;&gt;" +
                "&lt;a href=&quot;https://i.redd.it/m1.gif&quot;&gt;" +
                "&lt;img class=&quot;preview&quot; src=&quot;https://i.redd.it/m1.gif&quot;&gt;" +
                "&lt;/a&gt;&lt;/div&gt;&lt;/div&gt;"
        )

        val post = PostScraper(Dispatchers.Unconfined).scrap(html).data.children.first() as PostChild
        val picture = post.data.gallery.single()

        assertEquals("https://i.redd.it/m1.gif", picture.url)
        assertEquals("https://preview.redd.it/m1.gif?width=108", picture.thumbnail)
    }
}
