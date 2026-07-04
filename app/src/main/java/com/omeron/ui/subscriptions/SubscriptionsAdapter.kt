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
import com.omeron.data.model.db.Subscription
import com.omeron.databinding.ItemSubscriptionBinding

class SubscriptionsAdapter(
    private val listener: (String) -> Unit,
    private val onToggleHidden: (Subscription) -> Unit,
    private val onAddToMultireddit: (Subscription) -> Unit,
    private val onUnsubscribe: (Subscription) -> Unit,
    private val onLongClick: (Subscription) -> Unit
) : ListAdapter<Subscription, SubscriptionsAdapter.SubscriptionViewHolder>(
    SUBSCRIPTION_COMPARATOR
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return SubscriptionViewHolder(ItemSubscriptionBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SubscriptionViewHolder(
        private val binding: ItemSubscriptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(subscription: Subscription) {
            binding.subscription = subscription

            binding.subscriptionImage.load(subscription.icon) {
                crossfade(true)
                scale(Scale.FILL)
                precision(Precision.AUTOMATIC)
                placeholder(R.drawable.icon_reddit_placeholder)
                error(R.drawable.icon_reddit_placeholder)
                fallback(R.drawable.icon_reddit_placeholder)
            }

            binding.buttonHide.setImageResource(
                if (subscription.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
            binding.buttonHide.setOnClickListener { onToggleHidden(subscription) }
            binding.buttonAddMultireddit.setOnClickListener { onAddToMultireddit(subscription) }
            binding.buttonUnsubscribe.setOnClickListener { onUnsubscribe(subscription) }

            itemView.setOnClickListener {
                listener(subscription.name)
            }
            itemView.setOnLongClickListener {
                onLongClick(subscription)
                true
            }
        }
    }

    companion object {
        private val SUBSCRIPTION_COMPARATOR = object : DiffUtil.ItemCallback<Subscription>() {

            override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
                return oldItem.name == newItem.name
            }

            override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
                return oldItem == newItem
            }
        }
    }
}
