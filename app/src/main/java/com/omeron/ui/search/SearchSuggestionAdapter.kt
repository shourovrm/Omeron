package com.omeron.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.databinding.ItemSearchSuggestionCommunityBinding
import com.omeron.databinding.ItemSearchSuggestionRecentBinding
import com.omeron.databinding.ItemSearchSuggestionSectionBinding

class SearchSuggestionAdapter(
    private val onRecentQueryClick: (String) -> Unit,
    private val onRecentQueryRemove: (String) -> Unit,
    private val onCommunityClick: (String) -> Unit
) : ListAdapter<SearchSuggestionItem, RecyclerView.ViewHolder>(SUGGESTION_COMPARATOR) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SearchSuggestionItem.SectionHeader -> VIEW_TYPE_SECTION_HEADER
        is SearchSuggestionItem.RecentQuery -> VIEW_TYPE_RECENT_QUERY
        is SearchSuggestionItem.Community -> VIEW_TYPE_COMMUNITY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION_HEADER ->
                SectionHeaderViewHolder(ItemSearchSuggestionSectionBinding.inflate(inflater, parent, false))
            VIEW_TYPE_RECENT_QUERY ->
                RecentQueryViewHolder(ItemSearchSuggestionRecentBinding.inflate(inflater, parent, false))
            else ->
                CommunityViewHolder(ItemSearchSuggestionCommunityBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchSuggestionItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is SearchSuggestionItem.RecentQuery -> (holder as RecentQueryViewHolder).bind(item)
            is SearchSuggestionItem.Community -> (holder as CommunityViewHolder).bind(item)
        }
    }

    private class SectionHeaderViewHolder(
        private val binding: ItemSearchSuggestionSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchSuggestionItem.SectionHeader) {
            binding.sectionTitle.setText(item.titleRes)
        }
    }

    private inner class RecentQueryViewHolder(
        private val binding: ItemSearchSuggestionRecentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchSuggestionItem.RecentQuery) {
            binding.recentQuery.text = item.query
            binding.buttonRemove.contentDescription =
                binding.root.context.getString(R.string.search_remove_recent_description, item.query)

            binding.root.setOnClickListener { onRecentQueryClick(item.query) }
            binding.buttonRemove.setOnClickListener { onRecentQueryRemove(item.query) }
        }
    }

    private inner class CommunityViewHolder(
        private val binding: ItemSearchSuggestionCommunityBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchSuggestionItem.Community) {
            binding.communityAvatar.setText(item.name)
            binding.communityName.text =
                binding.root.context.getString(R.string.drawer_community_name, item.name)

            binding.root.setOnClickListener { onCommunityClick(item.name) }
        }
    }

    companion object {
        private const val VIEW_TYPE_SECTION_HEADER = 0
        private const val VIEW_TYPE_RECENT_QUERY = 1
        private const val VIEW_TYPE_COMMUNITY = 2

        private val SUGGESTION_COMPARATOR = object : DiffUtil.ItemCallback<SearchSuggestionItem>() {
            override fun areItemsTheSame(
                oldItem: SearchSuggestionItem,
                newItem: SearchSuggestionItem
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: SearchSuggestionItem,
                newItem: SearchSuggestionItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
