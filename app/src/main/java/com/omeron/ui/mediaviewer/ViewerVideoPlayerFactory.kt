package com.omeron.ui.mediaviewer

import android.content.Context
import com.omeron.data.model.GalleryMedia
import com.omeron.util.ExoPlayerHelper
import com.omeron.util.LinkUtil
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.HttpURLConnection

/**
 * Builds the looping ExoPlayer used by the media viewers. Owns the shared HTTP cache, so only one
 * factory may be alive at a time: two caches over the same directory make ExoPlayer throw.
 */
class ViewerVideoPlayerFactory(context: Context) {

    private val exoPlayerHelper by lazy { ExoPlayerHelper(context) }

    /**
     * Returns a player that is already preparing and playing, or null when the URL is invalid.
     * [onError] is called for failures the player could not recover from.
     */
    fun createLoopingPlayer(
        context: Context,
        video: GalleryMedia,
        onError: () -> Unit,
        onTracksChanged: (Tracks) -> Unit
    ): SimpleExoPlayer? {
        val url = video.url.toHttpUrlOrNull() ?: return null

        if (url.host.contains("redgifs", ignoreCase = true)) {
            val requestProperties = url
                .queryParameterNames
                .associateWith { url.queryParameter(it) ?: "" }

            exoPlayerHelper.setRequestProperties(requestProperties)
        }

        val player = SimpleExoPlayer.Builder(context)
            .setMediaSourceFactory(exoPlayerHelper.defaultMediaSourceFactory)
            .build()

        val videoItem = exoPlayerHelper.getMediaItem(video.url)

        if (video.sound != null) {
            val videoSource = exoPlayerHelper.getMediaSource(videoItem)
            val audioSource = exoPlayerHelper.getMediaSource(video.sound)
            player.setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else {
            player.setMediaSource(exoPlayerHelper.getMediaSource(videoItem))
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (video.sound != null && isErrorFromAudio(error)) {
                    // Retry without audio if the separate sound track is gone
                    player.setMediaItem(videoItem)
                    player.prepare()
                } else {
                    onError()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                onTracksChanged(tracks)
            }
        })

        player.apply {
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            play()
        }

        return player
    }

    fun clearCache() {
        exoPlayerHelper.clearCache()
    }

    private fun isErrorFromAudio(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            val cause = error.cause as? HttpDataSource.InvalidResponseCodeException
            cause?.dataSpec?.key?.let { link ->
                return (cause.responseCode == HttpURLConnection.HTTP_FORBIDDEN ||
                        cause.responseCode == HttpURLConnection.HTTP_NOT_FOUND) &&
                        LinkUtil.isRedditSoundTrack(link)
            }
        }
        return false
    }

    companion object {
        fun hasAudio(tracks: Tracks): Boolean {
            val groups = tracks.groups
            if (!groups.isEmpty()) {
                for (arrayIndex in 0 until groups.size) {
                    for (groupIndex in 0 until groups[arrayIndex].length) {
                        val sampleMimeType = groups[arrayIndex].getTrackFormat(groupIndex)
                            .sampleMimeType
                        if (sampleMimeType != null && sampleMimeType.contains("audio")) {
                            return true
                        }
                    }
                }
            }
            return false
        }
    }
}
