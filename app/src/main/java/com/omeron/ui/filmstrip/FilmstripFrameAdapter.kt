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
import com.omeron.databinding.ItemFilmstripImageBinding
import com.omeron.databinding.ItemFilmstripVideoBinding
import com.omeron.ui.mediaviewer.ZoomableImageTouchListener
import com.omeron.ui.mediaviewer.loadViewerImage

/**
 * Pages of the Filmstrip viewer. Frame ids are stable, so frames inserted when a gallery finishes
 * resolving do not move the page the user is on.
 *
 * Only the frame named by [activeFrameId] may play. The adapter tells a holder when it becomes
 * (in)active, and a holder also re-checks when it attaches to or detaches from the window.
 */
class FilmstripFrameAdapter(
    private val playback: FilmstripPlayback,
    private val onFrameClick: (FilmstripFrame) -> Unit,
    private val onRetryResolution: (FilmstripFrame) -> Unit
) : ListAdapter<FilmstripFrame, FilmstripFrameAdapter.FrameViewHolder>(FRAME_COMPARATOR) {

    private var activeFrameId: String? = null

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
        if (payloads.contains(ACTIVE_CHANGED)) {
            holder.updatePlayback()
        } else {
            super.onBindViewHolder(holder, position, payloads)
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

        init {
            binding.root.setOnClickListener {
                binding.iconPaused.isVisible = playback.togglePause()
            }
        }

        override fun bind(frame: FilmstripFrame) {
            binding.root.contentDescription = frame.post.title
            binding.imagePoster.load(frame.post.preview)
            binding.iconPaused.isVisible = false
            binding.infoRetry.hide()
        }

        override fun updatePlayback() {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val frame = getItem(position)
            val video = frame.media

            if (isActive && video != null && binding.root.isAttachedToWindow) {
                // Re-running for the frame that is already playing would restart it.
                if (binding.video.player == null) {
                    binding.iconPaused.isVisible = false
                    binding.infoRetry.hide()
                    playback.play(video, binding.video) { showRetry() }
                }
            } else {
                stopPlayback()
            }
        }

        override fun stopPlayback() {
            playback.stopIfPlayingIn(binding.video)
        }

        private fun showRetry() {
            binding.infoRetry.setActionClickListener {
                binding.infoRetry.hide()
                playback.stopIfPlayingIn(binding.video)
                updatePlayback()
            }
            binding.infoRetry.show()
        }
    }

    private companion object {
        const val VIEW_TYPE_IMAGE = 0
        const val VIEW_TYPE_VIDEO = 1

        val ACTIVE_CHANGED = Any()

        val FRAME_COMPARATOR = object : DiffUtil.ItemCallback<FilmstripFrame>() {
            override fun areItemsTheSame(oldItem: FilmstripFrame, newItem: FilmstripFrame) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FilmstripFrame, newItem: FilmstripFrame) =
                oldItem == newItem
        }
    }
}
