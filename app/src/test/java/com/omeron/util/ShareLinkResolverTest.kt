package com.omeron.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkResolverTest {

    @Test
    fun matchesSubredditShareLink() {
        assertTrue(ShareLinkResolver.isSharePath("/r/CUDA/s/0cry21duun"))
    }

    @Test
    fun matchesUserShareLink() {
        assertTrue(ShareLinkResolver.isSharePath("/u/someuser/s/0cry21duun"))
    }

    @Test
    fun matchesTrailingSlash() {
        assertTrue(ShareLinkResolver.isSharePath("/r/CUDA/s/0cry21duun/"))
    }

    @Test
    fun rejectsRegularPermalink() {
        assertFalse(ShareLinkResolver.isSharePath("/r/CUDA/comments/abc123/some_title"))
    }

    @Test
    fun rejectsNullPath() {
        assertFalse(ShareLinkResolver.isSharePath(null))
    }
}
