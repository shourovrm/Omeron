package com.omeron.ui.profilemanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.data.model.ProfileItem
import com.omeron.data.model.db.Profile
import com.omeron.databinding.ItemProfileSwitchBinding

class ProfileSwitcherAdapter(
    private val currentProfile: Profile?,
    private val onProfileClick: (Profile) -> Unit
) : ListAdapter<ProfileItem.UserProfile, ProfileSwitcherAdapter.ProfileSwitchViewHolder>(COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileSwitchViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ProfileSwitchViewHolder(ItemProfileSwitchBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ProfileSwitchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProfileSwitchViewHolder(
        private val binding: ItemProfileSwitchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProfileItem.UserProfile) {
            binding.profile = item.profile
            binding.currentCheck.isVisible = item.profile.id == currentProfile?.id
            itemView.setOnClickListener { onProfileClick(item.profile) }
        }
    }

    companion object {
        private val COMPARATOR = object : DiffUtil.ItemCallback<ProfileItem.UserProfile>() {
            override fun areItemsTheSame(
                oldItem: ProfileItem.UserProfile,
                newItem: ProfileItem.UserProfile
            ): Boolean = oldItem.profile.id == newItem.profile.id

            override fun areContentsTheSame(
                oldItem: ProfileItem.UserProfile,
                newItem: ProfileItem.UserProfile
            ): Boolean = oldItem == newItem
        }
    }
}
