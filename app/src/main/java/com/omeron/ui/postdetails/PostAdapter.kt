package com.omeron.ui.postdetails

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.request.ImageRequest
import com.omeron.R
import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.databinding.ItemPostHeaderBinding
import com.omeron.ui.common.widget.RedditView
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.util.extension.load
import com.omeron.util.extension.setRatio

class PostAdapter(
    private val contentPreferences: ContentPreferences,
    private val postClickListener: PostListAdapter.PostClickListener,
    private val onLinkClickListener: RedditView.OnLinkClickListener? = null
) : RecyclerView.Adapter<PostAdapter.ViewHolder>() {

    private var post: PostEntity? = null
    private var preview: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemPostHeaderBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        post?.let { holder.bind(it) }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            post?.let { holder.update(it) }
        }
    }

    override fun getItemCount(): Int = 1

    fun setPost(post: PostEntity, fromCache: Boolean) {
        // A null payload = full re-bind (re-runs the media `when(post.type)` block in bind());
        // a non-null payload = light update() only. When the authoritative post arrives with
        // different media than what's shown - e.g. a search result opens as a media-less stub
        // (type LINK) and the re-fetched real post is IMAGE/VIDEO - we must full re-bind or the
        // header stays frozen on the stub's rendering. Same-media updates stay cheap (no flicker).
        val mediaChanged = this.post?.type != post.type || this.post?.preview != post.preview
        var payload: Any? = null

        if (fromCache || this.post == null || mediaChanged) {
            preview = post.preview
        } else {
            payload = post
        }

        this.post = post

        notifyItemChanged(0, payload)
    }

    inner class ViewHolder(
        private val binding: ItemPostHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostEntity) {
            binding.includePostMetrics.post = post
            binding.includePostFlairs.post = post

            binding.includePostInfo.run {
                this.post = post
                textPostAuthor.text = post.author
                textSubreddit.text = post.subreddit
            }

            binding.textPostTitle.text = post.title

            binding.includePostMetrics.setRatio(post.ratio)

            binding.includePostInfo.groupCrosspost.isVisible = false
            binding.includePostInfo.textPostAuthor.apply {
                setTextColor(ContextCompat.getColor(context, post.posterType.color))
            }

            bindText(post)

            bindAwards(post)

            bindFlairs(post)

            when (post.type) {
                PostType.IMAGE -> {
                    bindImage(post) {
                        error(R.drawable.preview_image_fallback)
                        fallback(R.drawable.preview_image_fallback)
                    }
                    binding.imagePost.setOnClickListener { postClickListener.onImageClick(post) }
                }
                PostType.LINK -> {
                    bindImage(post) {
                        error(R.drawable.preview_link_fallback)
                        fallback(R.drawable.preview_link_fallback)
                    }
                    binding.imagePost.setOnClickListener { postClickListener.onLinkClick(post) }
                }
                PostType.VIDEO -> {
                    bindImage(post) {
                        error(R.drawable.preview_video_fallback)
                        fallback(R.drawable.preview_video_fallback)
                    }
                    binding.imagePost.setOnClickListener { postClickListener.onVideoClick(post) }
                }
                else -> {
                    // Ignore
                }
            }

            binding.buttonTypeIndicator.apply {
                when {
                    post.mediaType == MediaType.REDDIT_GALLERY ||
                            post.mediaType == MediaType.IMGUR_ALBUM ||
                            post.mediaType == MediaType.IMGUR_GALLERY -> {
                        visibility = View.VISIBLE
                        setIcon(R.drawable.ic_gallery)
                    }
                    post.type == PostType.VIDEO -> {
                        visibility = View.VISIBLE
                        setIcon(R.drawable.ic_play)
                    }
                    post.type == PostType.LINK -> {
                        isVisible = true
                        setIcon(R.drawable.ic_link)
                    }
                    else -> {
                        visibility = View.GONE
                    }
                }
            }

            binding.includePostMetrics.buttonMore.setOnClickListener {
                postClickListener.onMenuClick(post)
            }

            binding.includePostMetrics.buttonSave.setOnClickListener {
                postClickListener.onSaveClick(post)
            }

            when {
                post.crosspost != null -> {
                    binding.includeCrosspost.run {
                        root.isVisible = true
                        root.setOnClickListener { postClickListener.onClick(post.crosspost) }
                        title.text = post.crosspost.title
                        includePostInfo.post = post.crosspost
                        includePostInfo.textPostAuthor.text = post.crosspost.author
                        includePostInfo.textSubreddit.text = post.crosspost.subreddit
                        includePostInfo.groupCrosspost.isVisible = false
                    }
                }

                post.crosspostScrap != null -> {
                    binding.includeCrosspost.run {
                        root.isVisible = true
                        title.text = post.crosspostScrap?.title
                        includePostInfo.textPostAuthor.text = post.crosspostScrap?.author
                        includePostInfo.textSubreddit.text = post.crosspostScrap?.subreddit
                        includePostInfo.textPostDate.isVisible = false
                        includePostInfo.groupCrosspost.isVisible = false
                    }
                }

                else -> binding.includeCrosspost.root.isVisible = false
            }

            binding.includePostMetrics.buttonSave.isChecked = post.saved
        }

        fun update(post: PostEntity) {
            binding.includePostMetrics.post = post
            binding.includePostFlairs.post = post

            binding.includePostMetrics.buttonSave.isChecked = post.saved

            bindText(post)

            bindAwards(post)

            bindFlairs(post)
        }

        private fun bindText(post: PostEntity) {
            binding.textPost.apply {
                if (post.selfRedditText.isNotEmpty()) {
                    visibility = View.VISIBLE
                    setText(post.selfRedditText)
                    setOnLinkClickListener(onLinkClickListener)
                } else {
                    visibility = View.GONE
                }
            }
        }

        private fun bindFlairs(post: PostEntity) {
            when {
                post.hasFlairs -> {
                    binding.includePostFlairs.root.visibility = View.VISIBLE
                    binding.includePostFlairs.postFlair.apply {
                        if (!post.flair.isEmpty()) {
                            visibility = View.VISIBLE

                            setFlair(post.flair)
                        } else {
                            visibility = View.GONE
                        }
                    }
                }
                post.isSelf -> {
                    binding.includePostFlairs.root.visibility = View.GONE
                }
                else -> {
                    binding.includePostFlairs.postFlair.visibility = View.GONE
                }
            }
            binding.includePostInfo.postFlair.apply {
                if (!post.authorFlair.isEmpty()) {
                    visibility = View.VISIBLE

                    setFlair(post.authorFlair)
                } else {
                    visibility = View.GONE
                }
            }
        }

        private fun bindAwards(post: PostEntity) {
            binding.awards.apply {
                if (post.totalAwards > 0) {
                    visibility = View.VISIBLE
                    setAwards(post.awards)
                } else {
                    visibility = View.GONE
                }
            }
        }

        private fun bindImage(
            post: PostEntity,
            requestBuilder: ImageRequest.Builder.() -> Unit = {}
        ) {
            binding.imagePost.apply {
                visibility = View.VISIBLE
                load(preview, !post.shouldShowPreview(contentPreferences), builder = requestBuilder)
            }
        }
    }
}
