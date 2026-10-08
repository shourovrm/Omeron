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
import com.omeron.databinding.ItemProfileHistoryBinding
import com.omeron.databinding.ItemProfileLabelBinding
import com.omeron.util.DateUtil
import com.omeron.util.extension.load

class ProfileHistoryAdapter(
    private val onRowClick: (PostEntity) -> Unit,
    private val onRowLongClick: (PostEntity) -> Unit,
    private val onThumbnailClick: (PostEntity) -> Unit,
    private val onClearHistoryClick: () -> Unit
) : ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(HISTORY_COMPARATOR) {

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
        return when (viewType) {
            VIEW_TYPE_LABEL -> LabelViewHolder(ItemProfileLabelBinding.inflate(inflater, parent, false))
            VIEW_TYPE_ROW -> RowViewHolder(ItemProfileHistoryBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unknown type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryListItem.DayLabel -> (holder as LabelViewHolder).bind(item)
            is HistoryListItem.Row -> (holder as RowViewHolder).bind(item.post)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryListItem.DayLabel -> VIEW_TYPE_LABEL
            is HistoryListItem.Row -> VIEW_TYPE_ROW
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

            binding.root.setOnClickListener { onRowClick(post) }
            binding.root.setOnLongClickListener {
                onRowLongClick(post)
                true
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
                thumbnail.setOnClickListener { onThumbnailClick(post) }
            } else {
                thumbnail.setOnClickListener(null)
                thumbnail.isClickable = false
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_LABEL = 0
        private const val VIEW_TYPE_ROW = 1

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
