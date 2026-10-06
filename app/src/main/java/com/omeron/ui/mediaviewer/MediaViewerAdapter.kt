package com.omeron.ui.mediaviewer

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.omeron.data.model.GalleryMedia
import com.omeron.databinding.ItemImageBinding
import com.omeron.databinding.ItemVideoBinding
import com.omeron.util.extension.asBoolean
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Tracks

class MediaViewerAdapter(
    context: Context,
    var muteVideo: Boolean,
    private val onMediaClick: () -> Unit,
    private val showControls: (Boolean) -> Unit,
    private val hasAudio: (Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val media: MutableList<GalleryMedia> = mutableListOf()

    private val players: MutableList<Player> = mutableListOf()

    private val playerFactory by lazy { ViewerVideoPlayerFactory(context) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            GalleryMedia.Type.IMAGE.value -> ImageViewHolder(
                ItemImageBinding.inflate(inflater, parent, false)
            )
            GalleryMedia.Type.VIDEO.value -> VideoViewHolder(
                ItemVideoBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            GalleryMedia.Type.IMAGE.value -> (holder as ImageViewHolder).bind(media[position])
            GalleryMedia.Type.VIDEO.value -> (holder as VideoViewHolder).bind(media[position])
        }
    }

    override fun getItemCount(): Int {
        return media.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (media[position].type) {
            GalleryMedia.Type.IMAGE -> GalleryMedia.Type.IMAGE.value
            GalleryMedia.Type.VIDEO -> GalleryMedia.Type.VIDEO.value
        }
    }

    fun getItem(position: Int): GalleryMedia? {
        return media.getOrNull(position)
    }

    fun submitData(images: List<GalleryMedia>) {
        this.media.clear()
        this.media.addAll(images)
        notifyDataSetChanged()
    }

    fun clear() {
        for (player in players) {
            player.release()
        }
        players.clear()
        playerFactory.clearCache()
    }

    inner class ImageViewHolder(
        private val binding: ItemImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.image.run {
                setOnTouchListener(ZoomableImageTouchListener(this))
                setOnClickListener { onMediaClick.invoke() }
            }
        }

        fun bind(image: GalleryMedia) {
            loadImage(image)
            binding.infoRetry.setActionClickListener { loadImage(image) }
        }

        private fun loadImage(image: GalleryMedia) {
            binding.image.loadViewerImage(
                image.url,
                onStart = {
                    binding.loadingCradle.isVisible = true
                    binding.infoRetry.hide()
                },
                onCancel = { binding.loadingCradle.isVisible = false },
                onError = {
                    binding.loadingCradle.isVisible = false
                    showControls.invoke(false)
                    binding.infoRetry.show()
                },
                onSuccess = {
                    binding.loadingCradle.isVisible = false
                    showControls.invoke(true)
                }
            )
        }
    }

    inner class VideoViewHolder(
        private val binding: ItemVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: GalleryMedia) {
            val player = playerFactory.createLoopingPlayer(
                binding.video.context,
                video,
                onError = { binding.infoRetry.show() },
                onTracksChanged = ::initAudioVolume
            ) ?: return

            players.add(player)

            binding.video.run {
                this.player = player
                setControllerVisibilityListener { controllerVisibility ->
                    showControls.invoke(controllerVisibility.asBoolean)
                }
            }

            binding.infoRetry.setActionClickListener { player.prepare() }
        }

        private fun initAudioVolume(tracks: Tracks) {
            if (ViewerVideoPlayerFactory.hasAudio(tracks)) {
                muteAudio(muteVideo)
                hasAudio.invoke(true)
            } else {
                hasAudio.invoke(false)
            }
        }

        fun muteAudio(shouldMute: Boolean) {
            (binding.video.player as? SimpleExoPlayer)?.volume = if (shouldMute) 0F else 1F
        }
    }
}
