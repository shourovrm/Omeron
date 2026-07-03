package com.omeron.ui.subscriptions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.omeron.data.remote.api.reddit.model.AboutChild
import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.databinding.ItemMultiredditSubredditResultBinding
import com.omeron.util.extension.loadSubredditIcon

// ponytail: minimal adapter over the raw scraped Child instead of reusing SearchSubredditAdapter -
// that one is built around SubredditEntity (via SubredditMapper2's html parsing), which this
// name-only pick list doesn't need.
class MultiredditSubredditSearchAdapter(
    private val onClick: (String) -> Unit
) : PagingDataAdapter<Child, MultiredditSubredditSearchAdapter.ViewHolder>(COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemMultiredditSubredditResultBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val child = getItem(position) as? AboutChild ?: return
        holder.bind(child)
    }

    inner class ViewHolder(
        private val binding: ItemMultiredditSubredditResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(child: AboutChild) {
            binding.textSubredditName.text = child.data.displayName
            binding.imageSubreddit.loadSubredditIcon(child.data.getIcon())
            itemView.setOnClickListener { onClick(child.data.displayName) }
        }
    }

    companion object {
        private val COMPARATOR = object : DiffUtil.ItemCallback<Child>() {
            override fun areItemsTheSame(oldItem: Child, newItem: Child): Boolean {
                return (oldItem as? AboutChild)?.data?.displayName ==
                    (newItem as? AboutChild)?.data?.displayName
            }

            override fun areContentsTheSame(oldItem: Child, newItem: Child): Boolean {
                return oldItem == newItem
            }
        }
    }
}
