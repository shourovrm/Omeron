package com.omeron.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.db.SubredditEntity
import com.omeron.databinding.ItemSearchSubredditBinding
import com.omeron.util.extension.loadSubredditIcon

class SearchSubredditAdapter(
    private val onSubredditClick: (String) -> Unit,
    private val onJoinClick: (SubredditEntity) -> Unit
) : PagingDataAdapter<SubredditEntity, SearchSubredditAdapter.SubredditViewHolder>(
    SUBREDDIT_COMPARATOR
) {

    /** Lowercase names of the communities the current profile has joined. */
    var joinedNames: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, JOIN_STATE_CHANGED)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubredditViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return SubredditViewHolder(ItemSearchSubredditBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: SubredditViewHolder, position: Int) {
        val subreddit = getItem(position) ?: return
        holder.bind(subreddit)
    }

    override fun onBindViewHolder(
        holder: SubredditViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val subreddit = getItem(position) ?: return
        if (payloads.contains(JOIN_STATE_CHANGED)) {
            holder.bindJoinState(subreddit)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class SubredditViewHolder(
        private val binding: ItemSearchSubredditBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(subreddit: SubredditEntity) {
            val context = binding.root.context
            binding.subredditImage.loadSubredditIcon(subreddit.icon)
            binding.subredditName.text =
                context.getString(R.string.drawer_community_name, subreddit.displayName)
            binding.subredditDetail.text = detailText(subreddit)
            bindJoinState(subreddit)

            itemView.setOnClickListener { onSubredditClick(subreddit.displayName) }
            binding.buttonJoin.setOnClickListener { onJoinClick(subreddit) }
            binding.buttonJoined.setOnClickListener { onJoinClick(subreddit) }
        }

        fun bindJoinState(subreddit: SubredditEntity) {
            val isJoined = subreddit.displayName.lowercase() in joinedNames
            binding.buttonJoin.isVisible = !isJoined
            binding.buttonJoined.isVisible = isJoined

            val context = binding.root.context
            binding.buttonJoin.contentDescription =
                context.getString(R.string.search_join_description, subreddit.displayName)
            binding.buttonJoined.contentDescription =
                context.getString(R.string.search_joined_description, subreddit.displayName)
        }

        private fun detailText(subreddit: SubredditEntity): String {
            val context = binding.root.context
            val members = subreddit.getSubscribersCount()
            return when {
                members.isEmpty() -> subreddit.title
                subreddit.title.isBlank() -> context.getString(R.string.search_community_members, members)
                else -> context.getString(R.string.search_community_detail, members, subreddit.title)
            }
        }
    }

    companion object {
        private val JOIN_STATE_CHANGED = Any()

        private val SUBREDDIT_COMPARATOR = object : DiffUtil.ItemCallback<SubredditEntity>() {
            override fun areItemsTheSame(
                oldItem: SubredditEntity,
                newItem: SubredditEntity
            ): Boolean {
                return oldItem.displayName == newItem.displayName
            }

            override fun areContentsTheSame(
                oldItem: SubredditEntity,
                newItem: SubredditEntity
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
