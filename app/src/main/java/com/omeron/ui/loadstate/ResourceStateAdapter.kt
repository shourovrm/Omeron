package com.omeron.ui.loadstate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.Resource
import com.omeron.databinding.ItemResourceStateBinding
import com.omeron.util.extension.isLoginRequired

class ResourceStateAdapter(
    // Screens that cannot launch the login flow leave this out and keep the plain retry button.
    private val login: (() -> Unit)? = null,
    private val retry: () -> Unit
) : RecyclerView.Adapter<ResourceStateAdapter.ViewHolder>() {

    var resource: Resource<Any>? = null
        set(resource) {
            if (field != resource) {
                val oldItem = displayResourceStateAsItem(field)
                val newItem = displayResourceStateAsItem(resource)

                if (oldItem && !newItem) {
                    notifyItemRemoved(0)
                } else if (newItem && !oldItem) {
                    notifyItemInserted(0)
                } else if (oldItem && newItem) {
                    notifyItemChanged(0)
                }

                field = resource
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemResourceStateBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(resource)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    override fun getItemCount(): Int = if (displayResourceStateAsItem(resource)) 1 else 0

    private fun displayResourceStateAsItem(resource: Resource<Any>?): Boolean {
        return resource != null && (resource !is Resource.Success || isEmpty(resource))
    }

    private fun isEmpty(resource: Resource<Any>?): Boolean {
        return (resource?.dataValue as? List<*>)?.isEmpty() ?: false
    }

    inner class ViewHolder(
        private val binding: ItemResourceStateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var loginRequired = false

        init {
            // Retrying can never get past the login wall, so the button opens the login screen.
            binding.buttonRetry.setOnClickListener {
                if (loginRequired) login?.invoke() else retry.invoke()
            }
        }

        fun bind(resource: Resource<Any>?) {
            loginRequired = login != null &&
                (resource as? Resource.Error)?.throwable?.isLoginRequired == true
            binding.textError.setText(
                if (loginRequired) R.string.login_required_message else R.string.network_retry_message
            )
            binding.buttonRetry.setText(
                if (loginRequired) R.string.login_required_action else R.string.network_retry_action
            )
            binding.loadingCradle.isVisible = resource is Resource.Loading
            binding.buttonRetry.isVisible = resource is Resource.Error
            binding.textError.isVisible = resource is Resource.Error

            val isEmpty = isEmpty(resource)
            binding.emptyData.isVisible = isEmpty
            binding.textEmptyData.isVisible = isEmpty
        }

        fun unbind() {
            binding.loadingCradle.isVisible = false
        }
    }
}
