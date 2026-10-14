package com.omeron.ui.filmstrip

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.omeron.R
import com.omeron.databinding.ItemFilmstripThumbnailBinding

/**
 * The row of thumbnails above a gallery: one per frame of the gallery, with the frame on screen
 * at full strength and outlined, and the others dimmed. A tap reports the frame to open.
 */
class FilmstripThumbnailAdapter(
    private val onThumbnailClick: (FilmstripFrame) -> Unit
) : ListAdapter<FilmstripFrame, FilmstripThumbnailAdapter.ThumbnailViewHolder>(FRAME_COMPARATOR) {

    private var activeFrameId: String? = null

    fun setActiveFrameId(frameId: String) {
        if (activeFrameId == frameId) return
        val previousIndex = currentList.indexOfFirst { it.id == activeFrameId }
        val newIndex = currentList.indexOfFirst { it.id == frameId }
        activeFrameId = frameId
        if (previousIndex >= 0) notifyItemChanged(previousIndex, ACTIVE_CHANGED)
        if (newIndex >= 0) notifyItemChanged(newIndex, ACTIVE_CHANGED)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ThumbnailViewHolder(ItemFilmstripThumbnailBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: ThumbnailViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bindActiveState(getItem(position))
        }
    }

    inner class ThumbnailViewHolder(
        private val binding: ItemFilmstripThumbnailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val activeStrokeWidth = binding.root.resources.displayMetrics.density * ACTIVE_STROKE_DP

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onThumbnailClick(getItem(position))
            }
        }

        fun bind(frame: FilmstripFrame) {
            val thumbnailSize = binding.root.resources.getDimensionPixelSize(R.dimen.filmstrip_thumbnail_size)
            binding.image.contentDescription = binding.root.context.getString(
                R.string.filmstrip_thumbnail_description,
                frame.indexInSource + 1,
                frame.framesInSource
            )
            // The full image is what the page shows; the thumbnail only needs a small decode.
            // A video has no still to load, so it keeps the dark tile.
            binding.image.load(frame.media?.url?.takeUnless { frame.isVideo }) {
                size(thumbnailSize)
                scale(Scale.FILL)
                error(R.drawable.preview_image_fallback)
            }
            bindActiveState(frame)
        }

        fun bindActiveState(frame: FilmstripFrame) {
            val isActive = frame.id == activeFrameId
            binding.image.alpha = if (isActive) 1F else INACTIVE_ALPHA
            binding.image.strokeWidth = if (isActive) activeStrokeWidth else 0F
        }
    }

    private companion object {
        val ACTIVE_CHANGED = Any()

        const val INACTIVE_ALPHA = 0.55F
        const val ACTIVE_STROKE_DP = 2F

        val FRAME_COMPARATOR = object : DiffUtil.ItemCallback<FilmstripFrame>() {
            override fun areItemsTheSame(oldItem: FilmstripFrame, newItem: FilmstripFrame) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FilmstripFrame, newItem: FilmstripFrame) =
                oldItem == newItem
        }
    }
}
