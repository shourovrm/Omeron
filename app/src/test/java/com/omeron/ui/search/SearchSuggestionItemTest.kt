package com.omeron.ui.search

import com.omeron.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionItemTest {

    @Test
    fun `recent queries come before communities, each under its own header`() {
        val items = buildSearchSuggestions(listOf("kotlin", "rust"), listOf("android"))

        assertEquals(
            listOf(
                SearchSuggestionItem.SectionHeader(R.string.search_recent_section),
                SearchSuggestionItem.RecentQuery("kotlin"),
                SearchSuggestionItem.RecentQuery("rust"),
                SearchSuggestionItem.SectionHeader(R.string.search_communities_section),
                SearchSuggestionItem.Community("android")
            ),
            items
        )
    }

    @Test
    fun `a section without rows has no header`() {
        val onlyCommunities = buildSearchSuggestions(emptyList(), listOf("android"))
        val onlyRecent = buildSearchSuggestions(listOf("kotlin"), emptyList())

        assertEquals(2, onlyCommunities.size)
        assertEquals(SearchSuggestionItem.SectionHeader(R.string.search_communities_section), onlyCommunities.first())
        assertEquals(2, onlyRecent.size)
        assertEquals(SearchSuggestionItem.SectionHeader(R.string.search_recent_section), onlyRecent.first())
    }

    @Test
    fun `nothing to suggest is an empty list`() {
        assertTrue(buildSearchSuggestions(emptyList(), emptyList()).isEmpty())
    }
}
