package com.omeron.ui.filmstrip

import android.content.Context
import com.omeron.data.model.GalleryMedia
import com.omeron.ui.mediaviewer.ViewerVideoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView

/**
 * Plays at most one video at a time. Each video gets a fresh player that is released as soon as
 * its page is left, so a page that is swiped past never keeps decoding or playing audio.
 */
class FilmstripPlayback(
    context: Context,
    private val onAudioAvailable: (Boolean) -> Unit,
    // Receives the player when a video starts and null when it stops, so the viewer can attach
    // and detach its seek bar.
    private val onPlayerChanged: (Player?) -> Unit
) {

    private val applicationContext = context.applicationContext

    // Created on first use and dropped by release(): the factory owns an on-disk cache that only
    // one instance may hold. Two viewers can be alive at once (a feed viewer, then the post page
    // and a second viewer on top of it); the covered one halts, which releases its factory so the
    // top viewer can create its own.
    private var playerFactory: ViewerVideoPlayerFactory? = null

    private var player: SimpleExoPlayer? = null
    private var playerView: PlayerView? = null

    var isMuted = false
        set(value) {
            field = value
            applyVolume()
        }

    var isPaused = false
        private set

    // True while the viewer is hidden or in the background; nothing may start playing then.
    var isSuspended = false
        private set

    /** Starts [video] in [view], replacing whatever was playing. */
    fun play(video: GalleryMedia, view: PlayerView, onError: () -> Unit) {
        if (isSuspended) return
        stop()

        val factory = playerFactory ?: ViewerVideoPlayerFactory(applicationContext)
        playerFactory = factory

        val newPlayer = factory.createLoopingPlayer(
            applicationContext,
            video,
            onError = onError,
            onTracksChanged = { tracks ->
                val hasAudio = ViewerVideoPlayerFactory.hasAudio(tracks)
                onAudioAvailable(hasAudio)
                applyVolume()
            }
        ) ?: run {
            onError()
            return
        }

        isPaused = false
        player = newPlayer
        playerView = view
        view.player = newPlayer
        applyVolume()
        onPlayerChanged(newPlayer)
    }

    /** Stops playback if [view] is the one currently playing; other views are left alone. */
    fun stopIfPlayingIn(view: PlayerView) {
        if (playerView === view) stop()
    }

    fun stop() {
        onPlayerChanged(null)
        playerView?.player = null
        player?.release()
        player = null
        playerView = null
        isPaused = false
        onAudioAvailable(false)
    }

    /** Returns whether the video is paused after the toggle. */
    fun togglePause(): Boolean {
        val currentPlayer = player ?: return false
        isPaused = !isPaused
        currentPlayer.playWhenReady = !isPaused
        return isPaused
    }

    fun halt() {
        isSuspended = true
        release()
    }

    fun resume() {
        isSuspended = false
    }

    /** Releases the player and the shared HTTP cache; playback can start again afterwards. */
    fun release() {
        stop()
        playerFactory?.clearCache()
        playerFactory = null
    }

    private fun applyVolume() {
        player?.volume = if (isMuted) 0F else 1F
    }
}
