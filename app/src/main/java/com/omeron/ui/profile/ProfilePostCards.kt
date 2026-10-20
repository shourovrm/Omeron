package com.omeron.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.omeron.data.model.PostType
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.databinding.ItemPostCompactBinding
import com.omeron.databinding.ItemPostImageBinding
import com.omeron.databinding.ItemPostLinkBinding
import com.omeron.databinding.ItemPostTextBinding
import com.omeron.ui.common.widget.RedditView
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.ui.postlist.PostViewHolder
import com.omeron.util.ClickableMovementMethod

/**
 * The feed's post views (the four card types and the compact row) for the profile lists. The saved
 * posts tab and the history tab both show posts this way, so the creating, binding and click
 * routing live here once. A list adapter owns the item positions, so it passes in how to look a
 * post up and how to refresh a row.
 */
class ProfilePostCards(
    private val postClickListener: PostListAdapter.PostClickListener,
    onLinkClickListener: RedditView.OnLinkClickListener?,
    private val postAt: (position: Int) -> PostEntity?,
    private val onPostChanged: (position: Int, post: PostEntity) -> Unit
) {

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

    private val listener = object : PostListAdapter.Listener {
        override fun onClick(position: Int, isLong: Boolean) {
            postAt(position)?.let {
                if (isLong) {
                    postClickListener.onLongClick(it)
                } else {
                    postClickListener.onClick(it)
                }
            }
        }

        override fun onMediaClick(position: Int) {
            postAt(position)?.let {
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
            postAt(position)?.let {
                postClickListener.onMenuClick(it)
            }
        }

        override fun onSaveClick(position: Int) {
            postAt(position)?.let {
                postClickListener.onSaveClick(it)
                it.saved = !it.saved
                onPostChanged(position, it)
            }
        }
    }

    fun viewType(post: PostEntity, layout: PostLayout): Int {
        return if (layout == PostLayout.COMPACT) {
            PostListAdapter.COMPACT_VIEW_TYPE
        } else {
            post.type.value
        }
    }

    fun isPostViewType(viewType: Int): Boolean {
        return viewType == PostListAdapter.COMPACT_VIEW_TYPE ||
            PostType.values().any { it.value == viewType }
    }

    fun createViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            PostListAdapter.COMPACT_VIEW_TYPE -> PostViewHolder.CompactPostViewHolder(
                ItemPostCompactBinding.inflate(inflater, parent, false),
                listener
            )
            PostType.TEXT.value -> PostViewHolder.TextPostViewHolder(
                ItemPostTextBinding.inflate(inflater, parent, false),
                listener,
                clickableMovementMethod
            )
            PostType.IMAGE.value -> PostViewHolder.ImagePostViewHolder(
                ItemPostImageBinding.inflate(inflater, parent, false),
                listener
            )
            PostType.VIDEO.value -> PostViewHolder.VideoPostViewHolder(
                ItemPostImageBinding.inflate(inflater, parent, false),
                listener
            )
            PostType.LINK.value -> PostViewHolder.LinkPostViewHolder(
                ItemPostLinkBinding.inflate(inflater, parent, false),
                listener
            )
            else -> throw IllegalArgumentException("Unknown post view type $viewType")
        }
    }

    fun bind(holder: RecyclerView.ViewHolder, post: PostEntity, contentPreferences: ContentPreferences) {
        when (holder) {
            is PostViewHolder.CompactPostViewHolder -> holder.bind(post, contentPreferences)
            is PostViewHolder.TextPostViewHolder -> holder.bind(post, contentPreferences)
            is PostViewHolder.ImagePostViewHolder -> holder.bind(post, contentPreferences)
            is PostViewHolder.VideoPostViewHolder -> holder.bind(post, contentPreferences)
            is PostViewHolder.LinkPostViewHolder -> holder.bind(post, contentPreferences)
            else -> throw IllegalArgumentException("Not a post view holder: ${holder.javaClass}")
        }
    }
}
