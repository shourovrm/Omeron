package com.omeron.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.databinding.ItemProfileLabelBinding

/** One label row above the saved posts: how many are saved. Shows nothing for an empty list. */
class ProfileSavedCountAdapter : RecyclerView.Adapter<ProfileSavedCountAdapter.ViewHolder>() {

    var savedCount: Int = 0
        set(value) {
            val hadRow = field > 0
            field = value
            when {
                !hadRow && value > 0 -> notifyItemInserted(0)
                hadRow && value == 0 -> notifyItemRemoved(0)
                hadRow -> notifyItemChanged(0)
            }
        }

    override fun getItemCount(): Int = if (savedCount > 0) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemProfileLabelBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(savedCount)
    }

    class ViewHolder(private val binding: ItemProfileLabelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(savedCount: Int) {
            binding.labelTitle.text = itemView.context.getString(
                R.string.profile_saved_count,
                savedCount
            )
        }
    }
}
