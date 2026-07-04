package com.omeron.ui.subscriptions

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.R
import com.omeron.databinding.DialogMultiredditEditBinding
import com.omeron.util.extension.text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

// Reusable create+edit dialog: no id arg -> create mode (members buffered until Save, see
// MultiredditEditViewModel), id arg -> edit existing (rename + live member add/remove).
// Its own lifecycleScope is enough for the collectors below - it's cancelled automatically
// when the dialog is dismissed, unlike the old per-AlertDialog manual job.cancel() dance.
@AndroidEntryPoint
class MultiredditEditDialogFragment : DialogFragment() {

    private val viewModel: MultiredditEditViewModel by viewModels()

    // Activity-scoped: its coroutine scope survives this dialog's dismiss, so create/rename writes
    // actually complete (the dialog's own viewModelScope is cancelled the moment we dismiss()).
    private val subscriptionsViewModel: SubscriptionsViewModel by activityViewModels()

    private var _binding: DialogMultiredditEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMultiredditEditBinding.inflate(layoutInflater)
        val id = arguments?.getLong(ARG_ID, -1L)?.takeIf { it != -1L }
        viewModel.setId(id)

        setupChips()
        setupSubredditSearch()
        setupAddUser()

        lifecycleScope.launch {
            binding.inputName.editText?.setText(viewModel.getInitialName().orEmpty())
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setTitle(if (id == null) R.string.dialog_create_multireddit_title else R.string.dialog_edit_multireddit_title)
            .setPositiveButton(
                if (id == null) R.string.dialog_create_multireddit_button else R.string.dialog_ok,
                null
            )
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }

        if (id != null) {
            builder.setNeutralButton(R.string.dialog_delete_multireddit_button) { _, _ ->
                confirmDelete(id)
            }
        }

        return builder.create().apply {
            setOnShowListener {
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onSaveClicked(id) }
            }
        }
    }

    private fun setupChips() {
        lifecycleScope.launch {
            viewModel.subredditMembers.collect { members ->
                rebuildChips(binding.chipGroupSubreddits, members) { viewModel.removeSubreddit(it) }
            }
        }
        lifecycleScope.launch {
            viewModel.userMembers.collect { members ->
                rebuildChips(binding.chipGroupUsers, members) { viewModel.removeUser(it) }
            }
        }
    }

    private fun rebuildChips(group: ChipGroup, members: List<String>, onRemove: (String) -> Unit) {
        group.removeAllViews()
        members.forEach { name -> addMemberChip(group, name) { onRemove(name) } }
    }

    private fun addMemberChip(group: ChipGroup, name: String, onRemove: () -> Unit) {
        val chip = Chip(ContextThemeWrapper(requireContext(), R.style.ChipActionStyle)).apply {
            text = name
            isCloseIconVisible = true
            setOnCloseIconClickListener { onRemove() }
        }
        group.addView(chip)
    }

    // Search-then-select: type a subreddit name, pick from scraped results instead of
    // free-text (free text can't be verified against a real subreddit anyway).
    private fun setupSubredditSearch() {
        val searchAdapter = MultiredditSubredditSearchAdapter { name ->
            viewModel.addSubreddit(name)
            binding.inputAddSubreddit.text?.clear()
            binding.listSubredditSearchResults.isVisible = false
        }
        binding.listSubredditSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        val searchQuery = MutableStateFlow("")
        @OptIn(FlowPreview::class)
        lifecycleScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(PagingData.empty()) else viewModel.searchSubreddits(query)
                }
                .collect { pagingData -> searchAdapter.submitData(lifecycle, pagingData) }
        }

        binding.inputAddSubreddit.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            binding.listSubredditSearchResults.isVisible = query.isNotBlank()
            searchQuery.value = query
        }
    }

    private fun setupAddUser() {
        binding.buttonAddUser.setOnClickListener {
            val name = binding.inputAddUser.text?.toString()?.trim().orEmpty()
            if (name.isNotBlank()) {
                viewModel.addUser(name)
                binding.inputAddUser.text?.clear()
            }
        }
    }

    private fun onSaveClicked(id: Long?) {
        val name = binding.inputName.text().orEmpty()
        if (id == null) {
            if (name.isBlank()) {
                binding.inputName.error = getString(R.string.profile_blank_error)
                return
            }
            val (subreddits, users) = viewModel.pendingMembers()
            subscriptionsViewModel.createMultireddit(name, subreddits, users)
        } else if (name.isNotBlank()) {
            subscriptionsViewModel.renameMultireddit(id, name)
        }
        dismiss()
    }

    private fun confirmDelete(id: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_multireddit_title)
            .setMessage(R.string.dialog_delete_multireddit_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                viewModel.delete(id)
                dismiss()
            }
            .setNegativeButton(R.string.dialog_no) { dialog: DialogInterface, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "multireddit_id"
        private const val TAG = "MultiredditEditDialogFragment"

        fun show(fragmentManager: FragmentManager, id: Long? = null) {
            MultiredditEditDialogFragment().apply {
                arguments = bundleOf(ARG_ID to (id ?: -1L))
            }.show(fragmentManager, TAG)
        }
    }
}
