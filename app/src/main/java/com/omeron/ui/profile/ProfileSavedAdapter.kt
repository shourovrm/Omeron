package com.omeron.ui.profile

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.Block
import com.omeron.data.model.Comment
import com.omeron.data.model.HtmlBlock
import com.omeron.data.model.SavedItem
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.databinding.IncludePostInfoBinding
import com.omeron.databinding.ItemUserCommentBinding
import com.omeron.databinding.ItemUserCommentCompactBinding
import com.omeron.ui.common.widget.RedditView
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.ui.postlist.PostViewHolder
import com.omeron.ui.user.UserCommentsAdapter
import com.omeron.util.DateUtil
import com.omeron.util.extension.blurText

class ProfileSavedAdapter(
    context: Context,
    postClickListener: PostListAdapter.PostClickListener,
    private val commentClickListener: UserCommentsAdapter.CommentClickListener,
    private val onLinkClickListener: RedditView.OnLinkClickListener? = null,
) : ListAdapter<SavedItem, RecyclerView.ViewHolder>(SAVED_COMPARATOR) {

    private val colorPrimary by lazy {
        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorPrimary))
    }

    private val postCards = ProfilePostCards(
        postClickListener,
        onLinkClickListener,
        postAt = ::getPost,
        onPostChanged = { position, post -> notifyItemChanged(position, post) }
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

    // Every view type changes with the layout, so a full rebind is the right amount of work.
    var layout: PostLayout = PostLayout.CARD
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when {
            postCards.isPostViewType(viewType) -> postCards.createViewHolder(parent, viewType)
            viewType == COMMENT_TYPE -> CommentViewHolder(
                ItemUserCommentBinding.inflate(inflater, parent, false)
            )
            viewType == COMPACT_COMMENT_TYPE -> CompactCommentViewHolder(
                ItemUserCommentCompactBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CommentViewHolder -> holder.bind(getComment(position))
            is CompactCommentViewHolder -> holder.bind(getComment(position))
            else -> {
                val post = getPost(position)!!
                postCards.bind(holder, post, contentPreferences)
                showSavedAge(holder, post)
            }
        }
    }

    // The card shows the post age; on this tab the date it was saved is the useful one. A post
    // saved before the time was stored has none, and keeps the post age. The compact row has no
    // age line to replace.
    private fun showSavedAge(holder: RecyclerView.ViewHolder, post: PostEntity) {
        if (post.time <= 0 || holder is PostViewHolder.CompactPostViewHolder) return
        val postInfoView = holder.itemView.findViewById<View>(R.id.include_post_info)
        val postInfoBinding = DataBindingUtil.getBinding<IncludePostInfoBinding>(postInfoView)
            ?: return

        // The binding writes the post age on the next frame and would replace the saved age.
        postInfoBinding.executePendingBindings()
        val context = holder.itemView.context
        postInfoBinding.textPostDate.text = context.getString(
            R.string.profile_post_saved_age,
            DateUtil.getTimeDifference(context, post.time)
        )
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is SavedItem.Post -> postCards.viewType(item.post, layout)
            is SavedItem.Comment -> {
                if (layout == PostLayout.COMPACT) COMPACT_COMMENT_TYPE else COMMENT_TYPE
            }
            else -> -1
        }
    }

    private fun getPost(position: Int): PostEntity? {
        return (getItem(position) as? SavedItem.Post)?.post
    }

    private fun getComment(position: Int): Comment.CommentEntity {
        return (getItem(position) as SavedItem.Comment).comment
    }

    fun currentPosts(): List<PostEntity> {
        return currentList.filterIsInstance<SavedItem.Post>().map { it.post }
    }

    private inner class CommentViewHolder(
        private val binding: ItemUserCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment.CommentEntity) {
            binding.comment = comment
            binding.includeItemComment.comment = comment

            with(comment) {
                binding.includeItemComment.commentScore.blurText(scoreHidden)

                binding.includeItemComment.commentDate.run {
                    val timeDifference = DateUtil.getTimeDifference(context, created)
                    text = if (edited > -1) {
                        val editedTimeDifference = DateUtil.getTimeDifference(
                            context,
                            edited,
                            false
                        )
                        context.getString(
                            R.string.comment_date_edited,
                            timeDifference,
                            editedTimeDifference
                        )
                    } else {
                        timeDifference
                    }
                }

                binding.includeItemComment.commentColorIndicator.run {
                    visibility = View.VISIBLE
                    backgroundTintList = colorPrimary
                }

                binding.includeItemComment.commentFlair.run {
                    if (!flair.isEmpty()) {
                        visibility = View.VISIBLE

                        setFlair(flair)
                    } else {
                        visibility = View.GONE
                    }
                }

                binding.includeItemComment.commentAwards.run {
                    if (awards.isNotEmpty()) {
                        visibility = View.VISIBLE

                        setAwards(awards, totalAwards)
                    } else {
                        visibility = View.GONE
                    }
                }

            }

            binding.includeItemComment.commentBody.run {
                setText(comment.body)
                setOnLinkClickListener(onLinkClickListener)
                setOnClickListener { commentClickListener.onClick(comment) }
                setOnLongClickListener {
                    commentClickListener.onLongClick(comment)
                    true
                }
            }

            itemView.run {
                setOnClickListener { commentClickListener.onClick(comment) }
                setOnLongClickListener {
                    commentClickListener.onLongClick(comment)
                    true
                }
            }
        }
    }

    // The full comment body is rich content (code blocks, tables) that cannot be cut to two lines,
    // so the compact row shows only the first paragraph of text, which a TextView can ellipsize.
    private inner class CompactCommentViewHolder(
        private val binding: ItemUserCommentCompactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment.CommentEntity) {
            val context = itemView.context
            binding.textCommentInfo.text = context.getString(
                R.string.profile_comment_compact_info,
                comment.author,
                comment.subreddit,
                comment.getTimeDifference(context)
            )
            binding.textCommentBody.text = firstParagraph(comment)

            itemView.setOnClickListener { commentClickListener.onClick(comment) }
            itemView.setOnLongClickListener {
                commentClickListener.onLongClick(comment)
                true
            }
        }

        private fun firstParagraph(comment: Comment.CommentEntity): CharSequence {
            val firstText = comment.body.blocks.firstOrNull { it.type == HtmlBlock.BlockType.TEXT }
            return (firstText?.block as? Block.TextBlock)?.text ?: ""
        }
    }

    companion object {
        private const val COMMENT_TYPE = 99
        private const val COMPACT_COMMENT_TYPE = 98

        private val SAVED_COMPARATOR = object : DiffUtil.ItemCallback<SavedItem>() {
            override fun areItemsTheSame(oldItem: SavedItem, newItem: SavedItem): Boolean {
                return if (oldItem is SavedItem.Post && newItem is SavedItem.Post) {
                    oldItem.post.id == newItem.post.id
                } else if (oldItem is SavedItem.Comment && newItem is SavedItem.Comment) {
                    oldItem.comment.name == newItem.comment.name
                } else {
                    false
                }
            }

            override fun areContentsTheSame(oldItem: SavedItem, newItem: SavedItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
