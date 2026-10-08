package com.omeron.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUtilTest {

    @Test
    fun `query shorter than the minimum is not valid`() {
        assertFalse(SearchUtil.isQueryValid("ab"))
        assertTrue(SearchUtil.isQueryValid("abc"))
    }

    @Test
    fun `new query goes first`() {
        assertEquals(listOf("c", "a", "b"), SearchUtil.withRecentQuery(listOf("a", "b"), "c"))
    }

    @Test
    fun `repeated query moves to the front without a duplicate`() {
        val recent = SearchUtil.withRecentQuery(listOf("kotlin", "android", "rust"), "Android")

        assertEquals(listOf("Android", "kotlin", "rust"), recent)
    }

    @Test
    fun `query is trimmed before it is stored`() {
        assertEquals(listOf("space"), SearchUtil.withRecentQuery(emptyList(), "  space "))
    }

    @Test
    fun `recent queries are capped at the limit and the oldest drops out`() {
        val full = (1..SearchUtil.RECENT_QUERIES_LIMIT).map { "query$it" }

        val recent = SearchUtil.withRecentQuery(full, "newest")

        assertEquals(SearchUtil.RECENT_QUERIES_LIMIT, recent.size)
        assertEquals("newest", recent.first())
        assertFalse("query${SearchUtil.RECENT_QUERIES_LIMIT}" in recent)
    }

    @Test
    fun `community filter ignores case and sorts alphabetically`() {
        val names = listOf("Kotlin", "android", "AndroidDev", "rust")

        assertEquals(listOf("android", "AndroidDev"), SearchUtil.filterCommunityNames(names, "ANDROID"))
    }

    @Test
    fun `blank typed text keeps every community`() {
        val names = listOf("rust", "Android")

        assertEquals(listOf("Android", "rust"), SearchUtil.filterCommunityNames(names, ""))
        assertEquals(listOf("Android", "rust"), SearchUtil.filterCommunityNames(names, "  "))
    }
}
