package com.omeron.ui.filmstrip

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.omeron.R
import com.omeron.databinding.ItemFilmstripImageBinding
import com.omeron.databinding.ItemFilmstripVideoBinding
import com.omeron.ui.mediaviewer.ZoomableImageTouchListener
import com.omeron.ui.mediaviewer.loadViewerImage
import com.omeron.util.extension.showWithAlpha

/**
 * Pages of the Filmstrip viewer. Frame ids are stable, so frames inserted when a gallery finishes
 * resolving do not move the page the user is on.
 *
 * Only the frame named by [activeFrameId] may play. The adapter tells a holder when it becomes
 * (in)active, and a holder also re-checks when it attaches to or detaches from the window.
 *
 * A tap on any frame is reported through [onFrameClick]; pausing is done with the button in the
 * middle of a video. The button appears when a tap brings the controls back, and fades out again
 * after a moment while the video plays. While the video is paused it stays as long as
 * [areControlsVisible] is true.
 */
class FilmstripFrameAdapter(
    private val playback: FilmstripPlayback,
    private val onFrameClick: (FilmstripFrame) -> Unit,
    private val onRetryResolution: (FilmstripFrame) -> Unit
) : ListAdapter<FilmstripFrame, FilmstripFrameAdapter.FrameViewHolder>(FRAME_COMPARATOR) {

    private var activeFrameId: String? = null

    /** Mirrors the viewer's overlay, so the play/pause button comes and goes with it. */
    var areControlsVisible = true
        set(value) {
            if (field == value) return
            field = value
            val activeIndex = currentList.indexOfFirst { it.id == activeFrameId }
            if (activeIndex >= 0) notifyItemChanged(activeIndex, CONTROLS_CHANGED)
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    fun setActiveFrameId(frameId: String?) {
        if (activeFrameId == frameId) return
        val previousIndex = currentList.indexOfFirst { it.id == activeFrameId }
        val newIndex = currentList.indexOfFirst { it.id == frameId }
        activeFrameId = frameId
        if (previousIndex >= 0) notifyItemChanged(previousIndex, ACTIVE_CHANGED)
        if (newIndex >= 0) notifyItemChanged(newIndex, ACTIVE_CHANGED)
    }

    /** Asks the active frame to start playing again, after playback was suspended. */
    fun restartActive() {
        val activeIndex = currentList.indexOfFirst { it.id == activeFrameId }
        if (activeIndex >= 0) notifyItemChanged(activeIndex, ACTIVE_CHANGED)
    }

    /** True when the frame at [position] is an image the user has zoomed in on. */
    fun isFrameZoomedIn(position: Int, pagerRecyclerView: RecyclerView?): Boolean {
        val holder = pagerRecyclerView?.findViewHolderForAdapterPosition(position)
        return (holder as? ImageFrameViewHolder)?.isZoomedIn == true
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isVideo) VIEW_TYPE_VIDEO else VIEW_TYPE_IMAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_VIDEO) {
            VideoFrameViewHolder(ItemFilmstripVideoBinding.inflate(inflater, parent, false))
        } else {
            ImageFrameViewHolder(ItemFilmstripImageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: FrameViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: FrameViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when {
            payloads.isEmpty() -> super.onBindViewHolder(holder, position, payloads)
            payloads.contains(ACTIVE_CHANGED) -> holder.updatePlayback()
            else -> holder.updateControls()
        }
    }

    override fun onViewAttachedToWindow(holder: FrameViewHolder) {
        holder.updatePlayback()
    }

    override fun onViewDetachedFromWindow(holder: FrameViewHolder) {
        holder.stopPlayback()
    }

    override fun onViewRecycled(holder: FrameViewHolder) {
        holder.stopPlayback()
    }

    abstract inner class FrameViewHolder(root: View) : RecyclerView.ViewHolder(root) {

        protected val isActive: Boolean
            get() = bindingAdapterPosition != RecyclerView.NO_POSITION &&
                getItem(bindingAdapterPosition).id == activeFrameId

        abstract fun bind(frame: FilmstripFrame)

        open fun updatePlayback() = Unit

        open fun stopPlayback() = Unit

        /** [areControlsVisible] changed while this frame is on screen. */
        open fun updateControls() = Unit
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ImageFrameViewHolder(
        private val binding: ItemFilmstripImageBinding
    ) : FrameViewHolder(binding.root) {

        init {
            binding.image.run {
                setOnTouchListener(ZoomableImageTouchListener(this))
                setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) onFrameClick(getItem(position))
                }
            }
        }

        val isZoomedIn: Boolean
            get() = binding.image.isZoomed

        override fun bind(frame: FilmstripFrame) {
            binding.image.contentDescription = frame.post.title

            // A zoom left over from the previous frame this holder showed would carry over.
            binding.image.resetZoom()

            loadImage(frame, frame.media?.url ?: frame.post.preview)

            binding.loadingCradle.isVisible = frame.status == FrameStatus.LOADING
            if (frame.status == FrameStatus.FAILED) showRetry(frame) else binding.infoRetry.hide()
        }

        private fun loadImage(frame: FilmstripFrame, url: String?) {
            if (url.isNullOrBlank()) return

            // While the post is still being resolved the preview is only a stand-in, so the
            // spinner stays up and a failed preview is not worth a retry bar of its own.
            binding.image.loadViewerImage(
                url,
                onStart = {
                    binding.loadingCradle.isVisible = true
                    if (frame.status == FrameStatus.READY) binding.infoRetry.hide()
                },
                onCancel = { binding.loadingCradle.isVisible = frame.status == FrameStatus.LOADING },
                onError = {
                    binding.loadingCradle.isVisible = frame.status == FrameStatus.LOADING
                    if (frame.status == FrameStatus.READY) showRetry(frame)
                },
                onSuccess = {
                    binding.loadingCradle.isVisible = frame.status == FrameStatus.LOADING
                }
            )
        }

        private fun showRetry(frame: FilmstripFrame) {
            binding.infoRetry.setActionClickListener {
                if (frame.status == FrameStatus.READY) {
                    binding.infoRetry.hide()
                    loadImage(frame, frame.media?.url)
                } else {
                    onRetryResolution(frame)
                }
            }
            binding.infoRetry.show()
        }
    }

    inner class VideoFrameViewHolder(
        private val binding: ItemFilmstripVideoBinding
    ) : FrameViewHolder(binding.root) {

        private val isPlayingHere: Boolean
            get() = binding.video.player != null

        // The retry bar takes the button's place, and a failed video has nothing to pause.
        private var hasPlaybackFailed = false

        private val shouldShowPlayPause: Boolean
            get() = areControlsVisible && isPlayingHere && !hasPlaybackFailed

        private val fadeOutPlayPauseButton = Runnable {
            binding.buttonPlayPause.showWithAlpha(false, CONTROLS_FADE_MILLIS)
        }

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onFrameClick(getItem(position))
            }
            binding.buttonPlayPause.setOnClickListener {
                if (!isPlayingHere) return@setOnClickListener
                // A tap during the fade-out brings the button back to full strength.
                binding.buttonPlayPause.animate().cancel()
                binding.buttonPlayPause.alpha = 1F
                showPlayPauseIcon(isPaused = playback.togglePause())
                scheduleFadeWhilePlaying()
            }
        }

        override fun bind(frame: FilmstripFrame) {
            binding.root.contentDescription = frame.post.title
            binding.imagePoster.load(frame.post.preview)
            binding.infoRetry.hide()
            hasPlaybackFailed = false
            placePlayPauseButton()
        }

        override fun updateControls() {
            binding.buttonPlayPause.removeCallbacks(fadeOutPlayPauseButton)

            if (!shouldShowPlayPause) {
                if (binding.buttonPlayPause.isVisible) {
                    binding.buttonPlayPause.showWithAlpha(false, CONTROLS_FADE_MILLIS)
                }
                return
            }

            showPlayPauseIcon(playback.isPaused)
            if (binding.buttonPlayPause.isVisible) {
                binding.buttonPlayPause.animate().cancel()
                binding.buttonPlayPause.alpha = 1F
            } else {
                binding.buttonPlayPause.showWithAlpha(true, CONTROLS_FADE_MILLIS)
            }
            scheduleFadeWhilePlaying()
        }

        private fun scheduleFadeWhilePlaying() {
            binding.buttonPlayPause.removeCallbacks(fadeOutPlayPauseButton)
            if (!playback.isPaused) {
                binding.buttonPlayPause.postDelayed(fadeOutPlayPauseButton, PLAY_PAUSE_AUTO_FADE_MILLIS)
            }
        }

        /**
         * Puts the button in its final state at once, for changes that are not a user's tap. A
         * playing video starts without the button, so opening or swiping to a frame never
         * shows it; only a paused video keeps it.
         */
        private fun placePlayPauseButton() {
            val isPaused = isPlayingHere && playback.isPaused
            binding.buttonPlayPause.run {
                removeCallbacks(fadeOutPlayPauseButton)
                animate().cancel()
                alpha = 1F
                isVisible = shouldShowPlayPause && isPaused
            }
            showPlayPauseIcon(isPaused)
        }

        private fun showPlayPauseIcon(isPaused: Boolean) {
            binding.buttonPlayPause.run {
                setImageResource(if (isPaused) R.drawable.ic_play_exo else R.drawable.ic_pause_exo)
                contentDescription = context.getString(
                    if (isPaused) {
                        R.string.filmstrip_play_description
                    } else {
                        R.string.filmstrip_pause_description
                    }
                )
            }
        }

        override fun updatePlayback() {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val frame = getItem(position)
            val video = frame.media

            if (isActive && video != null && binding.root.isAttachedToWindow) {
                // Re-running for the frame that is already playing would restart it.
                if (!isPlayingHere) {
                    binding.infoRetry.hide()
                    hasPlaybackFailed = false
                    playback.play(video, binding.video) { showRetry() }
                    placePlayPauseButton()
                }
            } else {
                stopPlayback()
            }
        }

        override fun stopPlayback() {
            playback.stopIfPlayingIn(binding.video)
            placePlayPauseButton()
        }

        private fun showRetry() {
            binding.infoRetry.setActionClickListener {
                binding.infoRetry.hide()
                playback.stopIfPlayingIn(binding.video)
                updatePlayback()
            }
            hasPlaybackFailed = true
            placePlayPauseButton()
            binding.infoRetry.show()
        }
    }

    private companion object {
        const val VIEW_TYPE_IMAGE = 0
        const val VIEW_TYPE_VIDEO = 1

        val ACTIVE_CHANGED = Any()
        val CONTROLS_CHANGED = Any()

        // How long the button stays on a playing video after a tap brought it back.
        const val PLAY_PAUSE_AUTO_FADE_MILLIS = 2000L

        // Same length as the viewer's overlay fade, so the button and the overlay move together.
        const val CONTROLS_FADE_MILLIS = 200L

        val FRAME_COMPARATOR = object : DiffUtil.ItemCallback<FilmstripFrame>() {
            override fun areItemsTheSame(oldItem: FilmstripFrame, newItem: FilmstripFrame) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FilmstripFrame, newItem: FilmstripFrame) =
                oldItem == newItem
        }
    }
}
