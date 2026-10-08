package com.omeron.ui.profile

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.ui.postmenu.PostMenuFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileHistoryFragment : ProfileTabFragment<ProfileHistoryAdapter>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViewModel()
    }

    override fun createAdapter(): ProfileHistoryAdapter {
        return ProfileHistoryAdapter(
            onRowClick = ::onClick,
            onRowLongClick = ::onLongClick,
            onThumbnailClick = ::openMedia,
            onClearHistoryClick = ::confirmClearHistory
        )
    }

    override fun currentPosts(): List<PostEntity> = adapter.currentPosts()

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.historyItems, viewModel.contentPreferences) { items, preferences ->
                adapter.contentPreferences = preferences
                adapter.submitList(items)
                showEmptyState(items.isEmpty())
            }.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collect()
        }
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_clear_history_title)
            .setMessage(R.string.dialog_clear_history_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> viewModel.clearHistory() }
            .setNegativeButton(R.string.dialog_no) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onLongClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post, PostMenuFragment.MenuType.HISTORY)
    }
}
