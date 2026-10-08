package com.omeron.ui.profilemanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.ProfileItem
import com.omeron.data.model.db.Profile
import com.omeron.databinding.ItemNewProfileBinding
import com.omeron.databinding.ItemProfileBinding

class ProfileManagerAdapter(
    private val currentProfile: Profile?,
    private val profileClickListener: ProfileClickListener
) : ListAdapter<ProfileItem, RecyclerView.ViewHolder>(PROFILE_COMPARATOR) {

    interface ProfileClickListener {
        fun onProfileClick(profile: Profile)

        fun onEditProfileClick(profile: Profile)

        fun onNewProfileClick()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            Type.PROFILE.value -> {
                ProfileViewHolder(ItemProfileBinding.inflate(inflater, parent, false))
            }
            Type.NEW_PROFILE.value -> {
                NewProfileViewHolder(ItemNewProfileBinding.inflate(inflater, parent, false))
            }
            else -> throw IllegalArgumentException("Unknown type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            Type.PROFILE.value -> {
                val userProfile = getItem(position) as ProfileItem.UserProfile
                (holder as ProfileViewHolder).bind(userProfile)
            }
            Type.NEW_PROFILE.value -> {
                // Ignore
            }
            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)
            if (item is ProfileItem.UserProfile) {
                (holder as ProfileViewHolder).update(item)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ProfileItem.UserProfile -> Type.PROFILE.value
            is ProfileItem.NewProfile -> Type.NEW_PROFILE.value
        }
    }

    inner class ProfileViewHolder(
        private val binding: ItemProfileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProfileItem.UserProfile) {
            update(item)
        }

        // Also runs for a change payload, so the listeners must follow the updated profile.
        fun update(item: ProfileItem.UserProfile) {
            binding.profile = item.profile
            binding.profileSummary.text = summaryText(item)

            itemView.setOnClickListener { profileClickListener.onProfileClick(item.profile) }
            binding.editButton.setOnClickListener {
                profileClickListener.onEditProfileClick(item.profile)
            }
        }

        private fun summaryText(item: ProfileItem.UserProfile): String {
            val context = itemView.context
            val communities = context.resources.getQuantityString(
                R.plurals.profile_community_count,
                item.communityCount,
                item.communityCount
            )
            val saved = context.getString(R.string.profile_saved_count, item.savedPostCount)
            val counts = context.getString(R.string.profile_row_summary, communities, saved)
            return if (item.profile.id == currentProfile?.id) {
                context.getString(R.string.profile_row_summary_current, counts)
            } else {
                counts
            }
        }
    }

    inner class NewProfileViewHolder(
        binding: ItemNewProfileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener { profileClickListener.onNewProfileClick() }
        }
    }

    private enum class Type(val value: Int) {
        PROFILE(0), NEW_PROFILE(1)
    }

    companion object {
        private val PROFILE_COMPARATOR = object : DiffUtil.ItemCallback<ProfileItem>() {
            override fun areItemsTheSame(oldItem: ProfileItem, newItem: ProfileItem): Boolean {
                return if (oldItem is ProfileItem.UserProfile && newItem is ProfileItem.UserProfile) {
                    oldItem.profile.id == newItem.profile.id
                } else {
                    oldItem is ProfileItem.NewProfile && newItem is ProfileItem.NewProfile
                }
            }

            override fun areContentsTheSame(oldItem: ProfileItem, newItem: ProfileItem): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: ProfileItem, newItem: ProfileItem): Any? {
                return newItem
            }
        }
    }
}
