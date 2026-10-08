package com.omeron.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.User
import com.omeron.databinding.ItemSearchUserBinding
import com.omeron.util.extension.loadSubredditIcon

class SearchUserAdapter(
    private val listener: (String) -> Unit
) : PagingDataAdapter<User, SearchUserAdapter.UserViewHolder>(USER_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return UserViewHolder(ItemSearchUserBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position) ?: return
        holder.bind(user)
    }

    inner class UserViewHolder(
        private val binding: ItemSearchUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.userName.text =
                binding.root.context.getString(R.string.reddit_user_prefixed, user.displayName)
            binding.userImage.loadSubredditIcon(user.icon)

            itemView.setOnClickListener { listener(user.displayName) }
        }
    }

    companion object {
        private val USER_COMPARATOR = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
                return oldItem.displayName == newItem.displayName
            }

            override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
                return oldItem == newItem
            }
        }
    }
}
