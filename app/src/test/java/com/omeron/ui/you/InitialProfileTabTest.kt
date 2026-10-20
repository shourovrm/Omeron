package com.omeron.ui.you

import com.omeron.data.model.preferences.ProfileTab
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialProfileTabTest {

    @Test
    fun `each tab position maps to itself`() {
        ProfileTab.values().forEachIndexed { position, _ ->
            assertEquals(position, initialProfileTabPosition(position))
        }
    }

    @Test
    fun `a negative position falls back to the first tab`() {
        assertEquals(0, initialProfileTabPosition(-1))
    }

    @Test
    fun `a position past the last tab falls back to the last tab`() {
        assertEquals(ProfileTab.HISTORY.ordinal, initialProfileTabPosition(7))
    }
}
