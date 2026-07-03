package com.omeron.ui.subscriptions

import android.content.DialogInterface
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.R
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.databinding.DialogAddProfileBinding
import com.omeron.databinding.DialogMultiredditEditBinding
import com.omeron.databinding.FragmentMultiredditsBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.util.extension.applyWindowInsets
import com.omeron.util.extension.text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MultiredditsFragment : BaseFragment() {

    private var _binding: FragmentMultiredditsBinding? = null
    private val binding get() = _binding!!

    override val viewModel: SubscriptionsViewModel by activityViewModels()

    private lateinit var multiredditsAdapter: MultiredditsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMultiredditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        bindViewModel()
        binding.buttonCreateMultireddit.setOnClickListener { showCreateDialog() }
    }

    private fun initRecyclerView() {
        multiredditsAdapter = MultiredditsAdapter(
            onClick = { showEditDialog(it) },
            onToggleHidden = { viewModel.toggleMultiredditHidden(it.multireddit) },
            onDelete = { confirmDelete(it) }
        )

        binding.listMultireddits.apply {
            applyWindowInsets(left = false, top = false, right = false)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = multiredditsAdapter
        }
    }

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.multireddits
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { multireddits ->
                    multiredditsAdapter.submitList(multireddits)
                    binding.emptyData.isVisible = multireddits.isEmpty()
                    binding.textEmptyData.isVisible = multireddits.isEmpty()
                }
        }
    }

    // ponytail: creation is name-only (reuses the profile-name dialog layout); members are
    // added afterwards by tapping the row into the edit dialog. Keeps the create flow to a
    // single field instead of juggling pending members before the multireddit id exists.
    private fun showCreateDialog() {
        val createBinding = DialogAddProfileBinding.inflate(layoutInflater)
        createBinding.inputName.hint = getString(R.string.dialog_create_multireddit_hint)

        MaterialAlertDialogBuilder(requireContext())
            .setView(createBinding.root)
            .setTitle(R.string.dialog_create_multireddit_title)
            .setPositiveButton(R.string.dialog_create_multireddit_button) { _, _ ->
                // Ignore, overridden below
            }
            .setNeutralButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val name = createBinding.inputName.text().toString()
                    if (name.isBlank()) {
                        createBinding.inputName.error = getString(R.string.profile_blank_error)
                    } else {
                        viewModel.createMultireddit(name)
                        dismiss()
                    }
                }
            }
    }

    private fun showEditDialog(item: MultiredditWithMembers) {
        val editBinding = DialogMultiredditEditBinding.inflate(layoutInflater)
        editBinding.inputName.editText?.setText(item.multireddit.name)

        fun rebuildChips(current: MultiredditWithMembers) {
            editBinding.chipGroupSubreddits.removeAllViews()
            current.members
                .filter { MultiredditMemberType.fromValue(it.type) == MultiredditMemberType.SUBREDDIT }
                .forEach { member ->
                    addMemberChip(editBinding.chipGroupSubreddits, member.targetName) {
                        viewModel.removeMember(
                            item.multireddit.id,
                            member.targetName,
                            MultiredditMemberType.SUBREDDIT
                        )
                    }
                }

            editBinding.chipGroupUsers.removeAllViews()
            current.members
                .filter { MultiredditMemberType.fromValue(it.type) == MultiredditMemberType.USER }
                .forEach { member ->
                    addMemberChip(editBinding.chipGroupUsers, member.targetName) {
                        viewModel.removeMember(item.multireddit.id, member.targetName, MultiredditMemberType.USER)
                    }
                }
        }

        rebuildChips(item)

        // Member add/remove is persisted immediately, so keep the chips in sync with the DB
        // for as long as the dialog is showing.
        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.multireddits
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { list ->
                    list.find { it.multireddit.id == item.multireddit.id }?.let { rebuildChips(it) }
                }
        }

        // Search-then-select: type a subreddit name, pick from scraped results instead of
        // free-text (free text can't be verified against a real subreddit anyway).
        val searchAdapter = MultiredditSubredditSearchAdapter { name ->
            viewModel.addMember(item.multireddit.id, name, MultiredditMemberType.SUBREDDIT)
            editBinding.inputAddSubreddit.text?.clear()
            editBinding.listSubredditSearchResults.isVisible = false
        }
        editBinding.listSubredditSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        val searchQuery = MutableStateFlow("")
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        val searchJob = viewLifecycleOwner.lifecycleScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(PagingData.empty()) else viewModel.searchSubreddits(query)
                }
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { pagingData ->
                    searchAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                }
        }

        editBinding.inputAddSubreddit.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            editBinding.listSubredditSearchResults.isVisible = query.isNotBlank()
            searchQuery.value = query
        }

        editBinding.buttonAddUser.setOnClickListener {
            val name = editBinding.inputAddUser.text?.toString()?.trim().orEmpty()
            if (name.isNotBlank()) {
                viewModel.addMember(item.multireddit.id, name, MultiredditMemberType.USER)
                editBinding.inputAddUser.text?.clear()
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(editBinding.root)
            .setTitle(R.string.dialog_edit_multireddit_title)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                // Ignore, overridden below
            }
            .setNeutralButton(R.string.dialog_delete_multireddit_button) { _, _ -> confirmDelete(item) }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener {
                job.cancel()
                searchJob.cancel()
            }
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val name = editBinding.inputName.text().toString()
                    if (name.isNotBlank()) {
                        viewModel.renameMultireddit(item.multireddit.id, name)
                    }
                    dismiss()
                }
            }
    }

    private fun addMemberChip(group: ChipGroup, name: String, onRemove: () -> Unit) {
        val chip = Chip(ContextThemeWrapper(requireContext(), R.style.ChipActionStyle)).apply {
            text = name
            isCloseIconVisible = true
            setOnCloseIconClickListener { onRemove() }
        }
        group.addView(chip)
    }

    private fun confirmDelete(item: MultiredditWithMembers) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_multireddit_title)
            .setMessage(R.string.dialog_delete_multireddit_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> viewModel.deleteMultireddit(item.multireddit.id) }
            .setNegativeButton(R.string.dialog_no) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
