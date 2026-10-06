package com.omeron.ui.postlist

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.databinding.IncludePostFlairsBinding
import com.omeron.databinding.IncludePostInfoBinding
import com.omeron.databinding.IncludePostMetricsBinding
import com.omeron.databinding.ItemPostCompactBinding
import com.omeron.databinding.ItemPostFilmstripBinding
import com.omeron.databinding.ItemPostGalleryBinding
import com.omeron.databinding.ItemPostImageBinding
import com.omeron.databinding.ItemPostLinkBinding
import com.omeron.databinding.ItemPostTextBinding
import com.omeron.ui.common.widget.AwardView
import com.omeron.util.ClickableMovementMethod
import com.omeron.util.DateUtil
import com.omeron.util.extension.load
import com.omeron.util.extension.setRatio
import com.omeron.util.extension.setSaved

abstract class PostViewHolder(
    itemView: View,
    private val postInfoBinding: IncludePostInfoBinding,
    private val postMetricsBinding: IncludePostMetricsBinding,
    private val postFlairsBinding: IncludePostFlairsBinding,
    listener: PostListAdapter.Listener
) : RecyclerView.ViewHolder(itemView) {

    private val title = itemView.findViewById<TextView>(R.id.text_post_title)
    private val awards = itemView.findViewById<AwardView>(R.id.awards)

    init {
        itemView.apply {
            setOnClickListener {
                listener.onClick(bindingAdapterPosition)
            }
            setOnLongClickListener {
                listener.onClick(bindingAdapterPosition, true)
                return@setOnLongClickListener true
            }
        }

        postMetricsBinding.buttonMore.setOnClickListener {
            listener.onMenuClick(bindingAdapterPosition)
        }

        postMetricsBinding.buttonSave.setOnClickListener {
            listener.onSaveClick(bindingAdapterPosition)
        }
    }

    open fun bind(
        postEntity: PostEntity,
        contentPreferences: ContentPreferences
    ) {
        postMetricsBinding.post = postEntity
        postFlairsBinding.post = postEntity

        postInfoBinding.run {
            this.post = postEntity
            textPostAuthor.text = postEntity.author
            textSubreddit.text = postEntity.subreddit
        }

        title.apply {
            text = postEntity.title
            setTextColor(ContextCompat.getColor(context, postEntity.textColor))
        }

        postMetricsBinding.setRatio(postEntity.ratio)

        awards.apply {
            if (postEntity.awards.isNotEmpty()) {
                visibility = View.VISIBLE
                setAwards(postEntity.awards, postEntity.totalAwards)
            } else {
                visibility = View.GONE
            }
        }

        postInfoBinding.textPostAuthor.apply {
            setTextColor(ContextCompat.getColor(context, postEntity.authorColor))
        }

        when {
            postEntity.hasFlairs -> {
                postFlairsBinding.root.visibility = View.VISIBLE
                postFlairsBinding.postFlair.apply {
                    if (!postEntity.flair.isEmpty()) {
                        visibility = View.VISIBLE

                        setFlair(postEntity.flair)
                    } else {
                        visibility = View.GONE
                    }
                }
            }

            postEntity.isSelf -> {
                postFlairsBinding.root.visibility = View.GONE
            }

            else -> {
                postFlairsBinding.postFlair.visibility = View.GONE
            }
        }

        when {
            postEntity.crosspost != null -> {
                postInfoBinding.groupCrosspost.isVisible = true
                postInfoBinding.textCrosspostSubreddit.text = postEntity.crosspost.subreddit
                postInfoBinding.textCrosspostAuthor.text = postEntity.crosspost.author
            }

            postEntity.crosspostScrap != null -> {
                postInfoBinding.groupCrosspost.isVisible = true
                postInfoBinding.textCrosspostSubreddit.text = postEntity.crosspostScrap?.subreddit
                postInfoBinding.textCrosspostAuthor.text = postEntity.crosspostScrap?.author
            }

            else -> postInfoBinding.groupCrosspost.isVisible = false
        }

        postMetricsBinding.setSaved(postEntity.saved)
    }

    open fun update(post: PostEntity) {
        title.setTextColor(ContextCompat.getColor(title.context, post.textColor))
        postMetricsBinding.setSaved(post.saved)
    }

    class ImagePostViewHolder(
        private val binding: ItemPostImageBinding,
        listener: PostListAdapter.Listener
    ) : PostViewHolder(
        binding.root,
        binding.includePostInfo,
        binding.includePostMetrics,
        binding.includePostFlairs,
        listener
    ) {

        init {
            binding.imagePostPreview.setOnClickListener {
                listener.onMediaClick(bindingAdapterPosition)
            }
        }

        override fun bind(
            postEntity: PostEntity,
            contentPreferences: ContentPreferences
        ) {
            super.bind(postEntity, contentPreferences)

            binding.imagePostPreview.load(
                postEntity.preview,
                !postEntity.shouldShowPreview(contentPreferences)
            ) {
                error(R.drawable.preview_image_fallback)
                fallback(R.drawable.preview_image_fallback)
            }

            binding.buttonTypeIndicator.apply {
                when (postEntity.mediaType) {
                    MediaType.REDDIT_GALLERY, MediaType.IMGUR_ALBUM, MediaType.IMGUR_GALLERY -> {
                        visibility = View.VISIBLE
                        setIcon(R.drawable.ic_gallery)
                    }

                    else -> {
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    class VideoPostViewHolder(
        private val binding: ItemPostImageBinding,
        listener: PostListAdapter.Listener
    ) : PostViewHolder(
        binding.root,
        binding.includePostInfo,
        binding.includePostMetrics,
        binding.includePostFlairs,
        listener
    ) {

        init {
            binding.imagePostPreview.setOnClickListener {
                listener.onMediaClick(bindingAdapterPosition)
            }
        }

        override fun bind(
            postEntity: PostEntity,
            contentPreferences: ContentPreferences
        ) {
            super.bind(postEntity, contentPreferences)

            binding.imagePostPreview.load(
                postEntity.preview,
                !postEntity.shouldShowPreview(contentPreferences)
            ) {
                error(R.drawable.preview_video_fallback)
                fallback(R.drawable.preview_video_fallback)
            }

            binding.buttonTypeIndicator.apply {
                visibility = View.VISIBLE
                setIcon(R.drawable.ic_play)
            }
        }
    }

    class TextPostViewHolder(
        private val binding: ItemPostTextBinding,
        listener: PostListAdapter.Listener,
        clickableMovementMethod: ClickableMovementMethod
    ) : PostViewHolder(
        binding.root,
        binding.includePostInfo,
        binding.includePostMetrics,
        binding.includePostFlairs,
        listener
    ) {

        init {
            binding.textPostSelf.movementMethod = clickableMovementMethod
            binding.textPostSelf.setOnLongClickListener {
                listener.onClick(bindingAdapterPosition, true)
                true
            }
        }

        override fun bind(
            postEntity: PostEntity,
            contentPreferences: ContentPreferences
        ) {
            super.bind(postEntity, contentPreferences)

            val previewText = postEntity.previewText

            binding.textPostSelf.apply {
                if (postEntity.shouldShowPreview(contentPreferences) && previewText != null) {
                    visibility = View.VISIBLE
                    setText(previewText, false)
                } else {
                    visibility = View.GONE
                }
            }
        }
    }

    class LinkPostViewHolder(
        private val binding: ItemPostLinkBinding,
        listener: PostListAdapter.Listener
    ) : PostViewHolder(
        binding.root,
        binding.includePostInfo,
        binding.includePostMetrics,
        binding.includePostFlairs,
        listener
    ) {

        init {
            binding.imagePostLinkPreview.setOnClickListener {
                listener.onMediaClick(bindingAdapterPosition)
            }
        }

        override fun bind(
            postEntity: PostEntity,
            contentPreferences: ContentPreferences
        ) {
            super.bind(postEntity, contentPreferences)

            binding.imagePostLinkPreview.load(
                postEntity.preview,
                !postEntity.shouldShowPreview(contentPreferences)
            ) {
                error(R.drawable.preview_link_fallback)
                fallback(R.drawable.preview_link_fallback)
            }
        }
    }

    // ponytail: gallery mode collapses every post type into one compact tile, so this holder
    // does NOT extend PostViewHolder - it skips the metrics/flairs/awards rows entirely rather
    // than inflating and hiding them. Tap always opens the post (no separate media-click), which
    // keeps this to one Listener call instead of a per-type dispatch like ImagePostViewHolder.
    class GalleryPostViewHolder(
        private val binding: ItemPostGalleryBinding,
        listener: PostListAdapter.Listener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                listener.onClick(bindingAdapterPosition)
            }
            itemView.setOnLongClickListener {
                listener.onClick(bindingAdapterPosition, true)
                true
            }
        }

        fun bind(postEntity: PostEntity, contentPreferences: ContentPreferences) {
            binding.textGalleryTitle.apply {
                text = postEntity.title
                setTextColor(ContextCompat.getColor(context, postEntity.textColor))
            }
            binding.textGalleryScore.text = postEntity.score

            binding.imageGalleryPreview.load(
                postEntity.preview,
                !postEntity.shouldShowPreview(contentPreferences)
            ) {
                error(R.drawable.preview_image_fallback)
                fallback(R.drawable.preview_image_fallback)
            }

            // ponytail: single reused CardButton, same as ImagePostViewHolder/VideoPostViewHolder's
            // buttonTypeIndicator - video and gallery/album are mutually exclusive on a post, so
            // one icon slot covers both instead of two overlapping ImageViews.
            binding.buttonGalleryTypeIndicator.apply {
                when {
                    postEntity.type == PostType.VIDEO -> {
                        visibility = View.VISIBLE
                        setIcon(R.drawable.ic_play)
                    }

                    postEntity.mediaType == MediaType.REDDIT_GALLERY ||
                        postEntity.mediaType == MediaType.IMGUR_ALBUM ||
                        postEntity.mediaType == MediaType.IMGUR_GALLERY -> {
                        visibility = View.VISIBLE
                        setIcon(R.drawable.ic_gallery)
                    }

                    else -> visibility = View.GONE
                }
            }
        }
    }

    // Media-only grid tile: preview, a corner badge and nothing else. A tap opens the viewer
    // through its own Listener call; long-press is the same as on the gallery tile.
    class FilmstripPostViewHolder(
        private val binding: ItemPostFilmstripBinding,
        listener: PostListAdapter.Listener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                listener.onFilmstripClick(bindingAdapterPosition)
            }
            itemView.setOnLongClickListener {
                listener.onClick(bindingAdapterPosition, true)
                true
            }
        }

        fun bind(postEntity: PostEntity, contentPreferences: ContentPreferences) {
            binding.imageFilmstripPreview.load(
                postEntity.preview,
                !postEntity.shouldShowPreview(contentPreferences)
            ) {
                error(R.drawable.preview_image_fallback)
                fallback(R.drawable.preview_image_fallback)
            }

            bindBadge(postEntity)
            itemView.contentDescription = describe(postEntity)
            update(postEntity)
        }

        // Also called alone when a tap marks the post as seen, so the tile dims without a rebind.
        fun update(postEntity: PostEntity) {
            itemView.alpha = if (postEntity.seen) SEEN_TILE_ALPHA else 1F
        }

        private fun bindBadge(postEntity: PostEntity) {
            val galleryCount = postEntity.gallery.size
            when {
                postEntity.type == PostType.VIDEO -> showBadge(R.drawable.ic_play, null)

                postEntity.isGallery -> showBadge(
                    R.drawable.ic_gallery,
                    galleryCount.takeIf { it > 0 }?.toString()
                )

                else -> binding.badgeFilmstrip.isVisible = false
            }
        }

        private fun showBadge(iconRes: Int, countText: String?) {
            binding.badgeFilmstrip.isVisible = true
            binding.iconFilmstripBadge.setImageResource(iconRes)
            binding.textFilmstripBadgeCount.apply {
                text = countText
                isVisible = countText != null
            }
        }

        private fun describe(postEntity: PostEntity): String {
            val resources = itemView.resources
            val galleryCount = postEntity.gallery.size
            return when {
                postEntity.type == PostType.VIDEO ->
                    resources.getString(R.string.filmstrip_tile_video, postEntity.title)

                postEntity.isGallery && galleryCount > 0 -> resources.getQuantityString(
                    R.plurals.filmstrip_tile_gallery_count,
                    galleryCount,
                    galleryCount,
                    postEntity.title
                )

                postEntity.isGallery ->
                    resources.getString(R.string.filmstrip_tile_gallery, postEntity.title)

                else -> resources.getString(R.string.filmstrip_tile_image, postEntity.title)
            }
        }

        private companion object {
            // Dim enough to tell read posts apart at a glance, light enough to keep the photo legible.
            const val SEEN_TILE_ALPHA = 0.6F
        }
    }

    // ponytail: same shape as GalleryPostViewHolder (no metrics/flairs/awards rows), just a
    // horizontal row instead of a tile - title + subreddit/score on the left, small thumbnail
    // on the right. Tap opens the post; one Listener call, no per-type media dispatch.
    class CompactPostViewHolder(
        private val binding: ItemPostCompactBinding,
        listener: PostListAdapter.Listener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                listener.onClick(bindingAdapterPosition)
            }
            itemView.setOnLongClickListener {
                listener.onClick(bindingAdapterPosition, true)
                true
            }
        }

        fun bind(postEntity: PostEntity, contentPreferences: ContentPreferences) {
            binding.avatarCompactCommunity.setText(postEntity.subredditName)
            binding.textCompactSubreddit.text = postEntity.subreddit
            binding.textCompactAge.text = itemView.context.getString(
                R.string.post_age_separated,
                DateUtil.getTimeDifference(itemView.context, postEntity.created, false)
            )

            binding.textCompactTitle.apply {
                text = postEntity.title
                setTextColor(ContextCompat.getColor(context, postEntity.textColor))
            }

            // Plain text keeps the excerpt in the secondary colour; the styled preview text would
            // bring its own link and emphasis colours.
            val excerpt = postEntity.previewText
                ?.takeIf { postEntity.shouldShowPreview(contentPreferences) }
                ?.toString()
                ?.trim()
            binding.textCompactExcerpt.apply {
                text = excerpt
                isVisible = !excerpt.isNullOrEmpty()
            }

            binding.textCompactScore.text = postEntity.score
            binding.textCompactComments.text = postEntity.commentsNumber

            val hasPreview = !postEntity.preview.isNullOrEmpty()
            binding.imageCompactPreview.isVisible = hasPreview
            if (hasPreview) {
                binding.imageCompactPreview.load(
                    postEntity.preview,
                    !postEntity.shouldShowPreview(contentPreferences)
                ) {
                    error(R.drawable.image_placeholder)
                    fallback(R.drawable.image_placeholder)
                }
            }

            binding.buttonCompactTypeIndicator.apply {
                when {
                    !hasPreview -> visibility = View.GONE

                    postEntity.type == PostType.VIDEO -> {
                        visibility = View.VISIBLE
                        setIcon(R.drawable.ic_play)
                    }

                    postEntity.mediaType == MediaType.REDDIT_GALLERY ||
                        postEntity.mediaType == MediaType.IMGUR_ALBUM ||
                        postEntity.mediaType == MediaType.IMGUR_GALLERY -> {
                        visibility = View.VISIBLE
                        setIcon(R.drawable.ic_gallery)
                    }

                    else -> visibility = View.GONE
                }
            }
        }
    }

    class PollPostViewHolder() {

    }
}
