package com.omeron.ui.subscriptions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.databinding.ItemMultiredditBinding

class MultiredditsAdapter(
    private val onClick: (MultiredditWithMembers) -> Unit,
    private val onLongClick: (MultiredditWithMembers) -> Unit,
    private val onEdit: (MultiredditWithMembers) -> Unit,
    private val onToggleHidden: (MultiredditWithMembers) -> Unit,
    private val onDelete: (MultiredditWithMembers) -> Unit
) : ListAdapter<MultiredditWithMembers, MultiredditsAdapter.MultiredditViewHolder>(MULTIREDDIT_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MultiredditViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return MultiredditViewHolder(ItemMultiredditBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: MultiredditViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MultiredditViewHolder(
        private val binding: ItemMultiredditBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MultiredditWithMembers) {
            binding.multi = item.multireddit

            binding.multiredditMembersCount.text = binding.root.context.getString(
                R.string.multireddit_member_count,
                item.members.size
            )

            binding.buttonHide.setImageResource(
                if (item.multireddit.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
            binding.buttonHide.setOnClickListener { onToggleHidden(item) }
            binding.buttonEdit.setOnClickListener { onEdit(item) }
            binding.buttonDelete.setOnClickListener { onDelete(item) }

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    companion object {
        private val MULTIREDDIT_COMPARATOR = object : DiffUtil.ItemCallback<MultiredditWithMembers>() {

            override fun areItemsTheSame(
                oldItem: MultiredditWithMembers,
                newItem: MultiredditWithMembers
            ): Boolean {
                return oldItem.multireddit.id == newItem.multireddit.id
            }

            override fun areContentsTheSame(
                oldItem: MultiredditWithMembers,
                newItem: MultiredditWithMembers
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
