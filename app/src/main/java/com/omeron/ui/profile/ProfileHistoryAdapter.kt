package com.omeron.ui.profile

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.PostType
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.model.preferences.PostLayout
import com.omeron.databinding.ItemProfileHistoryBinding
import com.omeron.databinding.ItemProfileLabelBinding
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.util.DateUtil
import com.omeron.util.extension.load

class ProfileHistoryAdapter(
    private val postClickListener: PostListAdapter.PostClickListener,
    private val onClearHistoryClick: () -> Unit,
    onToggleRow: (rowId: String) -> Unit
) : ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(HISTORY_COMPARATOR) {

    private val rowSelection = ProfileRowSelection(onToggleRow)

    private val postCards = ProfilePostCards(
        postClickListener,
        rowSelection,
        onLinkClickListener = null,
        postAt = { position -> (getItem(position) as? HistoryListItem.Row)?.post },
        onPostChanged = { position, post -> notifyItemChanged(position, post) }
    )

    // Compact keeps the dense history rows; card shows each post as in the feed.
    var layout: PostLayout = PostLayout.COMPACT
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when {
            viewType == VIEW_TYPE_LABEL -> {
                LabelViewHolder(ItemProfileLabelBinding.inflate(inflater, parent, false))
            }
            viewType == VIEW_TYPE_ROW -> {
                RowViewHolder(ItemProfileHistoryBinding.inflate(inflater, parent, false))
            }
            postCards.isPostViewType(viewType) -> postCards.createViewHolder(parent, viewType)
            else -> throw IllegalArgumentException("Unknown type $viewType")
        }
    }

    /** Mirrors the tab's selection onto the rows; only their selection look is redrawn. */
    fun setSelection(isActive: Boolean, selectedIds: Set<String>) {
        if (rowSelection.update(isActive, selectedIds)) {
            notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            (getItem(position) as? HistoryListItem.Row)?.let {
                rowSelection.style(holder.itemView, it.post.id)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryListItem.DayLabel -> (holder as LabelViewHolder).bind(item)
            is HistoryListItem.Row -> {
                rowSelection.style(holder.itemView, item.post.id)
                when (holder) {
                    is RowViewHolder -> holder.bind(item.post)
                    else -> postCards.bind(holder, item.post, contentPreferences)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is HistoryListItem.DayLabel -> VIEW_TYPE_LABEL
            is HistoryListItem.Row -> {
                if (layout == PostLayout.COMPACT) VIEW_TYPE_ROW else postCards.viewType(item.post, layout)
            }
        }
    }

    fun currentPosts(): List<PostEntity> {
        return currentList.filterIsInstance<HistoryListItem.Row>().map { it.post }
    }

    private inner class LabelViewHolder(
        private val binding: ItemProfileLabelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.labelAction.setOnClickListener { onClearHistoryClick() }
        }

        fun bind(label: HistoryListItem.DayLabel) {
            binding.labelTitle.text = dayText(binding.root.context, label.day)
            binding.labelAction.isVisible = label.showsClearAction
        }

        private fun dayText(context: Context, day: HistoryDay): String {
            return when (day) {
                HistoryDay.Today -> context.getString(R.string.profile_history_today)
                HistoryDay.Yesterday -> context.getString(R.string.profile_history_yesterday)
                is HistoryDay.Date -> DateUtils.formatDateTime(
                    context,
                    day.startOfDayMillis,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
                        DateUtils.FORMAT_ABBREV_ALL
                )
            }
        }
    }

    private inner class RowViewHolder(
        private val binding: ItemProfileHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostEntity) {
            val context = binding.root.context
            binding.historyTitle.text = post.title
            binding.historySecondary.text = context.getString(
                R.string.profile_history_secondary,
                post.subreddit,
                DateUtil.getTimeDifference(context, post.time)
            )
            bindThumbnail(post)

            binding.root.setOnClickListener {
                rowSelection.onTap(post.id) { postClickListener.onClick(post) }
            }
            binding.root.setOnLongClickListener {
                rowSelection.onLongPress(post.id)
                true
            }
            binding.historyMore.setOnClickListener {
                rowSelection.onTap(post.id) { postClickListener.onMenuClick(post) }
            }
        }

        private fun bindThumbnail(post: PostEntity) {
            val thumbnail = binding.historyThumbnail
            // Text and link posts may carry a keyword such as "self" instead of an image address.
            val hasPreview = post.preview?.startsWith("http") == true
            if (hasPreview) {
                thumbnail.load(post.preview, !post.shouldShowPreview(contentPreferences))
            } else {
                thumbnail.setImageDrawable(null)
            }

            val opensViewer = post.type == PostType.IMAGE || post.type == PostType.VIDEO
            if (hasPreview && opensViewer) {
                thumbnail.setOnClickListener {
                    rowSelection.onTap(post.id) { openInViewer(post) }
                }
                // A clickable child would otherwise swallow the long press meant for the row.
                thumbnail.setOnLongClickListener {
                    rowSelection.onLongPress(post.id)
                    true
                }
            } else {
                thumbnail.setOnClickListener(null)
                thumbnail.setOnLongClickListener(null)
                thumbnail.isClickable = false
            }
        }

        private fun openInViewer(post: PostEntity) {
            if (post.type == PostType.VIDEO) {
                postClickListener.onVideoClick(post)
            } else {
                postClickListener.onImageClick(post)
            }
        }
    }

    companion object {
        // Negative so they never collide with the post view types the shared post cards use.
        private const val VIEW_TYPE_LABEL = -2
        private const val VIEW_TYPE_ROW = -3
        private const val SELECTION_PAYLOAD = "selection"

        private val HISTORY_COMPARATOR = object : DiffUtil.ItemCallback<HistoryListItem>() {
            override fun areItemsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean {
                return when {
                    oldItem is HistoryListItem.Row && newItem is HistoryListItem.Row -> {
                        oldItem.post.id == newItem.post.id
                    }
                    oldItem is HistoryListItem.DayLabel && newItem is HistoryListItem.DayLabel -> {
                        oldItem.day == newItem.day
                    }
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
