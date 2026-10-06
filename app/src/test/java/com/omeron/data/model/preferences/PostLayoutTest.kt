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

    @Test
    fun `filmstrip is persisted as 3 and existing values keep their numbers`() {
        assertEquals(0, PostLayout.CARD.value)
        assertEquals(1, PostLayout.GALLERY.value)
        assertEquals(2, PostLayout.COMPACT.value)
        assertEquals(3, PostLayout.FILMSTRIP.value)
        assertEquals(PostLayout.FILMSTRIP, PostLayout.fromValue(3))
    }

    @Test
    fun `next cycles through all four layouts`() {
        assertEquals(PostLayout.GALLERY, PostLayout.CARD.next())
        assertEquals(PostLayout.COMPACT, PostLayout.GALLERY.next())
        assertEquals(PostLayout.FILMSTRIP, PostLayout.COMPACT.next())
        assertEquals(PostLayout.CARD, PostLayout.FILMSTRIP.next())
    }

    @Test
    fun `screens without a grid show gallery for filmstrip and skip it when toggling`() {
        assertEquals(PostLayout.GALLERY, PostLayout.FILMSTRIP.withoutFilmstrip())
        assertEquals(PostLayout.COMPACT, PostLayout.FILMSTRIP.nextWithoutFilmstrip())
        assertEquals(PostLayout.CARD, PostLayout.COMPACT.nextWithoutFilmstrip())
        assertEquals(PostLayout.GALLERY, PostLayout.CARD.nextWithoutFilmstrip())
    }
}
