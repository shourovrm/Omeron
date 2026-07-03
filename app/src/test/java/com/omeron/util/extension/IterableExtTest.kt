package com.omeron.util.extension

import org.junit.Assert.assertEquals
import org.junit.Test

class IterableExtTest {

    @Test
    fun interlaceEmptyReturnsEmpty() {
        // Regression: maxOf() used to throw NoSuchElementException on empty input
        // (empty multireddit -> no source lists), force-closing the app.
        assertEquals(emptyList<Int>(), emptyList<List<Int>>().interlace())
    }

    @Test
    fun interlaceRoundRobinsAcrossLists() {
        val result = listOf(listOf(1, 2, 3), listOf(10, 20)).interlace()
        assertEquals(listOf(1, 10, 2, 20, 3), result)
    }
}
