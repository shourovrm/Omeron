package com.omeron.ui.profilemanager

import com.omeron.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileNameValidationTest {

    @Test
    fun `name of three to twenty characters is accepted`() {
        assertNull(profileNameErrorRes("abc", emptyList()))
        assertNull(profileNameErrorRes("a".repeat(20), emptyList()))
    }

    @Test
    fun `name outside three to twenty characters is rejected`() {
        assertEquals(R.string.profile_name_length_error, profileNameErrorRes("ab", emptyList()))
        assertEquals(
            R.string.profile_name_length_error,
            profileNameErrorRes("a".repeat(21), emptyList())
        )
    }

    @Test
    fun `name matching an existing profile is rejected ignoring case`() {
        assertEquals(
            R.string.profile_already_exists_error,
            profileNameErrorRes("work", listOf("Default", "Work"))
        )
    }
}
