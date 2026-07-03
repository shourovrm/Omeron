package com.omeron.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkUtilTest {

    @Test
    fun legacyDashVideoHasSeparateAudioTrack() {
        assertEquals(
            "https://v.redd.it/abc/DASH_AUDIO_128.mp4",
            LinkUtil.getRedditSoundTrackOrNull("https://v.redd.it/abc/DASH_720.mp4")
        )
    }

    @Test
    fun dashManifestHasNoSeparateAudioTrack() {
        // .mpd muxes its own audio -> must not build a bogus separate audio source
        assertNull(LinkUtil.getRedditSoundTrackOrNull("https://v.redd.it/abc/DASHPlaylist.mpd?a=1&v=1&f=sd"))
    }

    @Test
    fun hlsManifestHasNoSeparateAudioTrack() {
        assertNull(LinkUtil.getRedditSoundTrackOrNull("https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&v=1&f=sd"))
    }
}
