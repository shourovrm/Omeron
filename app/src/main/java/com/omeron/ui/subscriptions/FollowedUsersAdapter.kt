package com.omeron.ui.subscriptions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Precision
import coil.size.Scale
import com.omeron.R
import com.omeron.data.model.db.FollowedUser
import com.omeron.databinding.ItemFollowedUserBinding
import com.omeron.databinding.ItemSectionBinding

// ponytail: header + rows in one ListAdapter (instead of a second RecyclerView) so the
// "Users" section can sit right below the subs list inside a single ConcatAdapter.
class FollowedUsersAdapter(
    private val onClick: (String) -> Unit,
    private val onToggleHidden: (FollowedUser) -> Unit,
    private val onUnfollow: (FollowedUser) -> Unit
) : ListAdapter<FollowedUsersAdapter.Row, RecyclerView.ViewHolder>(ROW_COMPARATOR) {

    sealed class Row {
        object Header : Row()
        data class UserItem(val user: FollowedUser) : Row()
    }

    fun submitUsers(users: List<FollowedUser>) {
        submitList(if (users.isEmpty()) emptyList() else listOf(Row.Header) + users.map { Row.UserItem(it) })
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Row.Header -> VIEW_TYPE_HEADER
            is Row.UserItem -> VIEW_TYPE_USER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemSectionBinding.inflate(inflater, parent, false))
        } else {
            UserViewHolder(ItemFollowedUserBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind()
            is UserViewHolder -> holder.bind((getItem(position) as Row.UserItem).user)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.title.setText(R.string.subscriptions_users_header)
        }
    }

    inner class UserViewHolder(
        private val binding: ItemFollowedUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: FollowedUser) {
            binding.user = user

            binding.userImage.load(user.icon) {
                crossfade(true)
                scale(Scale.FILL)
                precision(Precision.AUTOMATIC)
                placeholder(R.drawable.icon_reddit_placeholder)
                error(R.drawable.icon_reddit_placeholder)
                fallback(R.drawable.icon_reddit_placeholder)
            }

            binding.buttonHide.setImageResource(
                if (user.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
            binding.buttonHide.setOnClickListener { onToggleHidden(user) }
            binding.buttonUnfollow.setOnClickListener { onUnfollow(user) }

            itemView.setOnClickListener { onClick(user.name) }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_USER = 1

        private val ROW_COMPARATOR = object : DiffUtil.ItemCallback<Row>() {

            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean {
                return when {
                    oldItem is Row.Header && newItem is Row.Header -> true
                    oldItem is Row.UserItem && newItem is Row.UserItem ->
                        oldItem.user.name == newItem.user.name
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean {
                return oldItem == newItem
            }
        }
    }
}
