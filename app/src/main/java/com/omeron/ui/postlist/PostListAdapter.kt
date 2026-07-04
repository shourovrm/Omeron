package com.omeron.ui.postlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.omeron.data.model.PostType
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.databinding.ItemPostCompactBinding
import com.omeron.databinding.ItemPostGalleryBinding
import com.omeron.databinding.ItemPostImageBinding
import com.omeron.databinding.ItemPostLinkBinding
import com.omeron.databinding.ItemPostTextBinding
import com.omeron.ui.common.widget.RedditView
import com.omeron.util.ClickableMovementMethod

class PostListAdapter(
    private val repository: PostListRepository,
    private val postClickListener: PostClickListener,
    private val onLinkClickListener: RedditView.OnLinkClickListener? = null
) : PagingDataAdapter<PostEntity, RecyclerView.ViewHolder>(POST_COMPARATOR) {

    interface PostClickListener {
        fun onClick(post: PostEntity)

        fun onLongClick(post: PostEntity)

        fun onMenuClick(post: PostEntity)

        fun onImageClick(post: PostEntity)

        fun onVideoClick(post: PostEntity)

        fun onLinkClick(post: PostEntity)

        fun onSaveClick(post: PostEntity)
    }

    interface Listener {
        fun onClick(position: Int, isLong: Boolean = false)

        fun onMediaClick(position: Int)

        fun onMenuClick(position: Int)

        fun onSaveClick(position: Int)
    }

    private val clickableMovementMethod = ClickableMovementMethod(
        object : ClickableMovementMethod.OnClickListener {
            override fun onLinkClick(link: String) {
                onLinkClickListener?.onLinkClick(link)
            }

            override fun onLinkLongClick(link: String) {
                onLinkClickListener?.onLinkLongClick(link)
            }

            override fun onClick() {
                // ignore
            }

            override fun onLongClick() {
                // ignore
            }
        }
    )

    var contentPreferences: ContentPreferences = ContentPreferences(
        showNsfw = false,
        showNsfwPreview = false,
        showSpoilerPreview = false
    )
        set(value) {
            if (field.showNsfwPreview != value.showNsfwPreview ||
                field.showSpoilerPreview != value.showSpoilerPreview
            ) {
                field = value
                notifyDataSetChanged()
            }
        }

    // Card vs gallery is a full re-layout (every view type changes), so a full rebind is fine here.
    var postLayout: PostLayout = PostLayout.CARD
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private val listener = object : Listener {
        override fun onClick(position: Int, isLong: Boolean) {
            getItem(position)?.let {
                if (isLong) {
                    postClickListener.onLongClick(it)
                } else {
                    setPostSeen(position, it)
                    postClickListener.onClick(it)
                }
            }
        }

        override fun onMediaClick(position: Int) {
            getItem(position)?.let {
                setPostSeen(position, it)
                when (it.type) {
                    PostType.IMAGE -> postClickListener.onImageClick(it)
                    PostType.LINK -> postClickListener.onLinkClick(it)
                    PostType.VIDEO -> postClickListener.onVideoClick(it)
                    else -> {
                        // ignore
                    }
                }
            }
        }

        override fun onMenuClick(position: Int) {
            getItem(position)?.let {
                postClickListener.onMenuClick(it)
            }
        }

        override fun onSaveClick(position: Int) {
            getItem(position)?.let {
                postClickListener.onSaveClick(it)
                it.saved = !it.saved
                notifyItemChanged(position, it)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            // Gallery tile (any post type, when postLayout == GALLERY)
            GALLERY_VIEW_TYPE -> PostViewHolder.GalleryPostViewHolder(
                ItemPostGalleryBinding.inflate(inflater, parent, false),
                listener
            )
            // Compact row (any post type, when postLayout == COMPACT)
            COMPACT_VIEW_TYPE -> PostViewHolder.CompactPostViewHolder(
                ItemPostCompactBinding.inflate(inflater, parent, false),
                listener
            )
            // Text post
            PostType.TEXT.value -> PostViewHolder.TextPostViewHolder(
                ItemPostTextBinding.inflate(inflater, parent, false),
                listener,
                clickableMovementMethod
            )
            // Image post
            PostType.IMAGE.value -> PostViewHolder.ImagePostViewHolder(
                ItemPostImageBinding.inflate(inflater, parent, false),
                listener
            )
            // Video post
            PostType.VIDEO.value -> PostViewHolder.VideoPostViewHolder(
                ItemPostImageBinding.inflate(inflater, parent, false),
                listener
            )
            // Link post
            PostType.LINK.value -> PostViewHolder.LinkPostViewHolder(
                ItemPostLinkBinding.inflate(inflater, parent, false),
                listener
            )
            else -> throw IllegalArgumentException("Unknown type $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (postLayout == PostLayout.GALLERY) return GALLERY_VIEW_TYPE
        if (postLayout == PostLayout.COMPACT) return COMPACT_VIEW_TYPE
        return getItem(position)?.type?.value ?: -1
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return

        when (getItemViewType(position)) {
            // Gallery tile
            GALLERY_VIEW_TYPE -> (holder as PostViewHolder.GalleryPostViewHolder).bind(
                item,
                contentPreferences
            )
            // Compact row
            COMPACT_VIEW_TYPE -> (holder as PostViewHolder.CompactPostViewHolder).bind(
                item,
                contentPreferences
            )
            // Text post
            PostType.TEXT.value -> (holder as PostViewHolder.TextPostViewHolder).bind(
                item,
                contentPreferences
            )
            // Image post
            PostType.IMAGE.value -> (holder as PostViewHolder.ImagePostViewHolder).bind(
                item,
                contentPreferences
            )
            // Video post
            PostType.VIDEO.value -> (holder as PostViewHolder.VideoPostViewHolder).bind(
                item,
                contentPreferences
            )
            // Link post
            PostType.LINK.value -> (holder as PostViewHolder.LinkPostViewHolder).bind(
                item,
                contentPreferences
            )
            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position) ?: return
            (holder as? PostViewHolder)?.update(item)
        }
    }

    private fun setPostSeen(position: Int, post: PostEntity) {
        post.seen = true
        notifyItemChanged(position, post)
    }

    companion object {
        // Distinct from every PostType.value (0..3) so gallery mode can unify all post types
        // into a single compact view type.
        const val GALLERY_VIEW_TYPE = 100
        const val COMPACT_VIEW_TYPE = 101

        private val POST_COMPARATOR = object : DiffUtil.ItemCallback<PostEntity>() {
            override fun areItemsTheSame(oldItem: PostEntity, newItem: PostEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PostEntity, newItem: PostEntity): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: PostEntity, newItem: PostEntity): Any {
                return newItem
            }
        }
    }
}
