package com.omeron.ui.drawer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.databinding.ItemDrawerMessageBinding
import com.omeron.databinding.ItemDrawerRowBinding
import com.omeron.databinding.ItemDrawerSectionBinding

class DrawerAdapter(
    private val onMultiredditClick: (multiredditId: Long) -> Unit,
    private val onCommunityClick: (subredditName: String) -> Unit,
    private val onMultiredditLongClick: (DrawerItem.MultiredditRow) -> Unit,
    private val onCommunityLongClick: (DrawerItem.CommunityRow) -> Unit,
    private val onManageClick: (DrawerItem.ManageTarget) -> Unit
) : ListAdapter<DrawerItem, RecyclerView.ViewHolder>(DRAWER_ITEM_COMPARATOR) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DrawerItem.SectionHeader -> VIEW_TYPE_SECTION_HEADER
        is DrawerItem.Message -> VIEW_TYPE_MESSAGE
        is DrawerItem.MultiredditRow, is DrawerItem.CommunityRow -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION_HEADER ->
                SectionHeaderViewHolder(ItemDrawerSectionBinding.inflate(inflater, parent, false))
            VIEW_TYPE_MESSAGE ->
                MessageViewHolder(ItemDrawerMessageBinding.inflate(inflater, parent, false))
            else -> RowViewHolder(ItemDrawerRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DrawerItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is DrawerItem.Message -> (holder as MessageViewHolder).bind(item)
            is DrawerItem.MultiredditRow -> (holder as RowViewHolder).bind(item)
            is DrawerItem.CommunityRow -> (holder as RowViewHolder).bind(item)
        }
    }

    private inner class SectionHeaderViewHolder(
        private val binding: ItemDrawerSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DrawerItem.SectionHeader) {
            binding.sectionTitle.setText(item.titleRes)
            binding.sectionManage.setOnClickListener { onManageClick(item.manageTarget) }
        }
    }

    private class MessageViewHolder(
        private val binding: ItemDrawerMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DrawerItem.Message) {
            binding.message.setText(item.textRes)
        }
    }

    private inner class RowViewHolder(
        private val binding: ItemDrawerRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DrawerItem.MultiredditRow) {
            binding.rowAvatar.visibility = View.GONE
            binding.rowIcon.visibility = View.VISIBLE
            binding.rowName.text = item.name
            binding.rowEndText.visibility = View.VISIBLE
            binding.rowEndText.text = binding.root.context.getString(
                R.string.multireddit_member_count,
                item.memberCount
            )
            binding.root.setOnClickListener { onMultiredditClick(item.multiredditId) }
            binding.root.setOnLongClickListener {
                onMultiredditLongClick(item)
                true
            }
        }

        fun bind(item: DrawerItem.CommunityRow) {
            binding.rowIcon.visibility = View.GONE
            binding.rowAvatar.visibility = View.VISIBLE
            binding.rowAvatar.setText(item.subredditName)
            binding.rowName.text =
                binding.root.context.getString(R.string.drawer_community_name, item.subredditName)
            binding.rowEndText.visibility = View.GONE
            binding.root.setOnClickListener { onCommunityClick(item.subredditName) }
            binding.root.setOnLongClickListener {
                onCommunityLongClick(item)
                true
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SECTION_HEADER = 0
        private const val VIEW_TYPE_MESSAGE = 1
        private const val VIEW_TYPE_ROW = 2

        private val DRAWER_ITEM_COMPARATOR = object : DiffUtil.ItemCallback<DrawerItem>() {

            override fun areItemsTheSame(oldItem: DrawerItem, newItem: DrawerItem): Boolean {
                return when {
                    oldItem is DrawerItem.MultiredditRow && newItem is DrawerItem.MultiredditRow ->
                        oldItem.multiredditId == newItem.multiredditId
                    oldItem is DrawerItem.CommunityRow && newItem is DrawerItem.CommunityRow ->
                        oldItem.subredditName == newItem.subredditName
                    else -> oldItem == newItem
                }
            }

            override fun areContentsTheSame(oldItem: DrawerItem, newItem: DrawerItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
