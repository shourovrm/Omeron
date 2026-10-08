package com.omeron.ui.subscriptions

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.databinding.ItemDrawerMessageBinding
import com.omeron.databinding.ItemManageRowBinding
import com.omeron.databinding.ItemManageSectionBinding
import com.omeron.util.DateUtil

/** Rows shared by the manage communities and manage multireddits lists. */
class ManageAdapter(
    private val onRowClick: (ManageItem) -> Unit,
    private val onToggleHidden: (ManageItem) -> Unit,
    private val onMoreClick: (ManageItem) -> Unit
) : ListAdapter<ManageItem, RecyclerView.ViewHolder>(MANAGE_ITEM_COMPARATOR) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ManageItem.SectionLabel -> VIEW_TYPE_SECTION_LABEL
        is ManageItem.Message -> VIEW_TYPE_MESSAGE
        is ManageItem.CommunityRow, is ManageItem.MultiredditRow -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION_LABEL ->
                SectionLabelViewHolder(ItemManageSectionBinding.inflate(inflater, parent, false))
            VIEW_TYPE_MESSAGE ->
                MessageViewHolder(ItemDrawerMessageBinding.inflate(inflater, parent, false))
            else -> RowViewHolder(ItemManageRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ManageItem.SectionLabel -> (holder as SectionLabelViewHolder).bind(item)
            is ManageItem.Message -> (holder as MessageViewHolder).bind(item)
            is ManageItem.CommunityRow -> (holder as RowViewHolder).bind(item)
            is ManageItem.MultiredditRow -> (holder as RowViewHolder).bind(item)
        }
    }

    private class SectionLabelViewHolder(
        private val binding: ItemManageSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ManageItem.SectionLabel) {
            binding.sectionTitle.setText(item.titleRes)
            binding.sectionCount.text = item.count.toString()
        }
    }

    private class MessageViewHolder(
        private val binding: ItemDrawerMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ManageItem.Message) {
            binding.message.setText(item.textRes)
        }
    }

    private inner class RowViewHolder(
        private val binding: ItemManageRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context: Context get() = binding.root.context

        fun bind(item: ManageItem.CommunityRow) {
            binding.rowIcon.visibility = View.GONE
            binding.rowAvatar.visibility = View.VISIBLE
            binding.rowAvatar.setText(item.name)
            binding.rowName.text = if (item.isUser) {
                context.getString(R.string.manage_user_name, item.name)
            } else {
                context.getString(R.string.drawer_community_name, item.name)
            }
            binding.rowSecondary.text = joinedText(item)
            bindActions(item, item.isHidden)
        }

        fun bind(item: ManageItem.MultiredditRow) {
            binding.rowAvatar.visibility = View.GONE
            binding.rowIcon.visibility = View.VISIBLE
            binding.rowName.text = item.name
            binding.rowSecondary.text = memberSummaryText(item)
            bindActions(item, item.isHidden)
        }

        private fun bindActions(item: ManageItem, isHidden: Boolean) {
            // Hidden rows are dimmed but their buttons stay at full strength so Show is findable.
            val dimmedAlpha = if (isHidden) HIDDEN_ROW_ALPHA else 1f
            binding.rowIcon.alpha = dimmedAlpha
            binding.rowAvatar.alpha = dimmedAlpha
            binding.rowText.alpha = dimmedAlpha

            binding.buttonToggleHidden.setImageResource(
                if (isHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
            binding.buttonToggleHidden.contentDescription = context.getString(
                if (isHidden) R.string.manage_show else R.string.manage_hide
            )

            binding.root.setOnClickListener { onRowClick(item) }
            binding.buttonToggleHidden.setOnClickListener { onToggleHidden(item) }
            binding.buttonMore.setOnClickListener { onMoreClick(item) }
        }

        private fun joinedText(item: ManageItem.CommunityRow): String {
            val joinedAgo = DateUtil.getTimeDifference(context, item.joinedTimeMillis)
            return if (item.multiredditNames.isEmpty()) {
                context.getString(R.string.manage_joined, joinedAgo)
            } else {
                context.getString(
                    R.string.manage_joined_in,
                    joinedAgo,
                    item.multiredditNames.joinToString(", ")
                )
            }
        }

        private fun memberSummaryText(item: ManageItem.MultiredditRow): String {
            val memberCount = context.getString(R.string.multireddit_member_count, item.memberCount)
            if (item.memberPreviewNames.isEmpty()) return memberCount

            val hasMoreMembers = item.memberCount > item.memberPreviewNames.size
            val preview = item.memberPreviewNames.joinToString(", ") +
                if (hasMoreMembers) context.getString(R.string.manage_more_members_suffix) else ""
            return context.getString(R.string.manage_multireddit_summary, memberCount, preview)
        }
    }

    companion object {
        private const val VIEW_TYPE_SECTION_LABEL = 0
        private const val VIEW_TYPE_MESSAGE = 1
        private const val VIEW_TYPE_ROW = 2

        private const val HIDDEN_ROW_ALPHA = 0.5f

        private val MANAGE_ITEM_COMPARATOR = object : DiffUtil.ItemCallback<ManageItem>() {

            override fun areItemsTheSame(oldItem: ManageItem, newItem: ManageItem): Boolean {
                return when {
                    oldItem is ManageItem.CommunityRow && newItem is ManageItem.CommunityRow ->
                        oldItem.name == newItem.name && oldItem.isUser == newItem.isUser
                    oldItem is ManageItem.MultiredditRow && newItem is ManageItem.MultiredditRow ->
                        oldItem.id == newItem.id
                    oldItem is ManageItem.SectionLabel && newItem is ManageItem.SectionLabel ->
                        oldItem.titleRes == newItem.titleRes
                    else -> oldItem == newItem
                }
            }

            override fun areContentsTheSame(oldItem: ManageItem, newItem: ManageItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
