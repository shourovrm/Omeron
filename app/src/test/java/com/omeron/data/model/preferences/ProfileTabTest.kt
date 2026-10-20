package com.omeron.data.model.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileTabTest {

    @Test
    fun `nothing stored gives the tab default`() {
        assertEquals(PostLayout.CARD, ProfileTab.SAVED_POSTS.layoutFromStoredValue(null))
        assertEquals(PostLayout.CARD, ProfileTab.SAVED_COMMENTS.layoutFromStoredValue(null))
        assertEquals(PostLayout.COMPACT, ProfileTab.HISTORY.layoutFromStoredValue(null))
    }

    @Test
    fun `stored card and compact values are read back`() {
        assertEquals(
            PostLayout.COMPACT,
            ProfileTab.SAVED_POSTS.layoutFromStoredValue(PostLayout.COMPACT.value)
        )
        assertEquals(
            PostLayout.CARD,
            ProfileTab.HISTORY.layoutFromStoredValue(PostLayout.CARD.value)
        )
    }

    @Test
    fun `a stored layout the tab does not offer gives the tab default`() {
        assertEquals(
            PostLayout.COMPACT,
            ProfileTab.HISTORY.layoutFromStoredValue(PostLayout.GALLERY.value)
        )
        assertEquals(PostLayout.CARD, ProfileTab.SAVED_POSTS.layoutFromStoredValue(99))
    }

    @Test
    fun `toggling twice returns to the first layout`() {
        val tab = ProfileTab.SAVED_COMMENTS

        assertEquals(PostLayout.COMPACT, tab.toggled(PostLayout.CARD))
        assertEquals(PostLayout.CARD, tab.toggled(PostLayout.COMPACT))
    }

    @Test
    fun `pager positions map to the tabs in order`() {
        assertEquals(ProfileTab.SAVED_POSTS, ProfileTab.fromPagerPosition(0))
        assertEquals(ProfileTab.SAVED_COMMENTS, ProfileTab.fromPagerPosition(1))
        assertEquals(ProfileTab.HISTORY, ProfileTab.fromPagerPosition(2))
    }

    @Test
    fun `each tab stores under its own name`() {
        val names = ProfileTab.values().map { it.storageName }

        assertEquals(names.distinct(), names)
        assertNotEquals("", names.first())
    }
}
