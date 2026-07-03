package com.omeron.data.model.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves PostLayout.resolve() picks the per-subreddit override over the global default,
 * and falls back to CARD when neither is set.
 */
class PostLayoutTest {

    @Test
    fun `subreddit override wins over global default`() {
        assertEquals(
            PostLayout.GALLERY,
            PostLayout.resolve(subredditOverride = PostLayout.GALLERY.value, globalValue = PostLayout.CARD.value)
        )
        assertEquals(
            PostLayout.CARD,
            PostLayout.resolve(subredditOverride = PostLayout.CARD.value, globalValue = PostLayout.GALLERY.value)
        )
    }

    @Test
    fun `falls back to global default when no override is set`() {
        assertEquals(
            PostLayout.GALLERY,
            PostLayout.resolve(subredditOverride = null, globalValue = PostLayout.GALLERY.value)
        )
    }

    @Test
    fun `falls back to CARD when neither override nor global is set`() {
        assertEquals(PostLayout.CARD, PostLayout.resolve(subredditOverride = null, globalValue = null))
    }

    @Test
    fun `fromValue defaults to CARD for unknown values`() {
        assertEquals(PostLayout.CARD, PostLayout.fromValue(-1))
        assertEquals(PostLayout.GALLERY, PostLayout.fromValue(1))
    }
}
