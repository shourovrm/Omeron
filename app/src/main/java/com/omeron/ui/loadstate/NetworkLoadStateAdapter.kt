package com.omeron.ui.loadstate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.databinding.ItemLoadStateBinding
import com.omeron.util.extension.isLoginRequired

class NetworkLoadStateAdapter(
    // Screens that cannot launch the login flow leave this out and keep the plain retry footer.
    private val login: (() -> Unit)? = null,
    private val retry: () -> Unit
) : LoadStateAdapter<NetworkLoadStateAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemLoadStateBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    inner class ViewHolder(
        private val binding: ItemLoadStateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var loginRequired = false

        init {
            // Retrying can never get past the login wall, so the button opens the login screen.
            binding.buttonRetry.setOnClickListener {
                if (loginRequired) login?.invoke() else retry.invoke()
            }
        }

        fun bind(loadState: LoadState) {
            loginRequired = login != null &&
                (loadState as? LoadState.Error)?.error?.isLoginRequired == true
            binding.textError.setText(
                if (loginRequired) R.string.login_required_message else R.string.network_retry_message
            )
            binding.buttonRetry.setText(
                if (loginRequired) R.string.login_required_action else R.string.network_retry_action
            )
            binding.loadingCradle.isVisible = loadState is LoadState.Loading
            binding.buttonRetry.isVisible = loadState !is LoadState.Loading
            binding.textError.isVisible = loadState !is LoadState.Loading
        }

        fun unbind() {
            binding.loadingCradle.isVisible = false
        }
    }
}
