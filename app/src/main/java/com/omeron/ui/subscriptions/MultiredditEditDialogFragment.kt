package com.omeron.ui.subscriptions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import com.omeron.R
import com.omeron.databinding.DialogMultiredditEditBinding
import com.omeron.databinding.ItemMultiredditMemberBinding
import com.omeron.util.extension.text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

// Reusable create+edit page shown as a full-screen dialog: no id arg -> create mode (members
// buffered until Save, see MultiredditEditViewModel), id arg -> edit existing (rename + live
// member add/remove). Its own lifecycleScope is enough for the collectors below - it's cancelled
// automatically when the page is dismissed.
@AndroidEntryPoint
class MultiredditEditDialogFragment : DialogFragment() {

    private val viewModel: MultiredditEditViewModel by viewModels()

    // Activity-scoped: its coroutine scope survives this page's dismiss, so create/rename writes
    // actually complete (the page's own viewModelScope is cancelled the moment we dismiss()).
    private val subscriptionsViewModel: SubscriptionsViewModel by activityViewModels()

    private var _binding: DialogMultiredditEditBinding? = null
    private val binding get() = _binding!!

    private var multiredditId: Long? = null

    // Latest members, to keep a name from being added twice.
    private var subredditMembers: List<String> = emptyList()
    private var userMembers: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullscreenDialogTheme)

        multiredditId = arguments?.getLong(ARG_ID, -1L)?.takeIf { it != -1L }
        viewModel.setId(multiredditId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMultiredditEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initAppBar()
        initSectionHeaders()
        bindMembers()
        setupSubredditSearch()

        viewLifecycleOwner.lifecycleScope.launch {
            binding.inputName.editText?.setText(viewModel.getInitialName().orEmpty())
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun initAppBar() {
        binding.appBar.run {
            backCard.setIcon(R.drawable.ic_close)
            backCard.setOnClickListener { dismiss() }
            label.setText(
                if (multiredditId == null) R.string.multireddit_new_title else R.string.multireddit_edit_title
            )
            actionButton.setText(R.string.multireddit_save)
            actionButton.isVisible = true
            actionButton.setOnClickListener { onSaveClicked() }
        }
    }

    private fun initSectionHeaders() {
        binding.nameHeader.sectionTitle.setText(R.string.multireddit_name_label)
        binding.nameHeader.sectionCount.isVisible = false
        binding.membersHeader.sectionTitle.setText(R.string.drawer_section_communities)
        binding.addHeader.sectionTitle.setText(R.string.multireddit_add_label)
        binding.addHeader.sectionCount.isVisible = false
    }

    private fun bindMembers() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.subredditMembers, viewModel.userMembers) { subreddits, users ->
                subreddits to users
            }.collect { (subreddits, users) ->
                subredditMembers = subreddits
                userMembers = users
                rebuildMemberRows()
            }
        }
    }

    private fun rebuildMemberRows() {
        binding.memberContainer.removeAllViews()
        subredditMembers.forEach { name ->
            addMemberRow(getString(R.string.drawer_community_name, name), name) {
                viewModel.removeSubreddit(name)
            }
        }
        userMembers.forEach { name ->
            addMemberRow(getString(R.string.manage_user_name, name), name) {
                viewModel.removeUser(name)
            }
        }
        binding.membersHeader.sectionCount.text = (subredditMembers.size + userMembers.size).toString()
    }

    private fun addMemberRow(displayName: String, avatarText: String, onRemove: () -> Unit) {
        val rowBinding = ItemMultiredditMemberBinding.inflate(layoutInflater, binding.memberContainer, false)
        rowBinding.memberAvatar.setText(avatarText)
        rowBinding.memberName.text = displayName
        rowBinding.buttonRemove.contentDescription = getString(R.string.multireddit_remove_member, displayName)
        rowBinding.buttonRemove.setOnClickListener { onRemove() }
        binding.memberContainer.addView(rowBinding.root)
    }

    // Search-then-select: type a subreddit name, pick from scraped results instead of
    // free-text (free text can't be verified against a real subreddit anyway). A name typed
    // with a u/ prefix, or any typed name, can also be added as a user.
    private fun setupSubredditSearch() {
        val searchAdapter = MultiredditSubredditSearchAdapter { name -> addSubreddit(name) }
        binding.listSubredditSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        val subredditSearchQuery = MutableStateFlow("")
        @OptIn(FlowPreview::class)
        viewLifecycleOwner.lifecycleScope.launch {
            subredditSearchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(PagingData.empty()) else viewModel.searchSubreddits(query)
                }
                .collect { pagingData -> searchAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData) }
        }

        binding.buttonAddUser.setOnClickListener { addUser(typedMemberName()) }

        binding.inputAddMember.doOnTextChanged { text, _, _, _ ->
            val typedName = typedMemberName()
            val isUserName = USER_PREFIXES.any { text.toString().trim().startsWith(it, ignoreCase = true) }

            binding.listSubredditSearchResults.isVisible = typedName.isNotBlank() && !isUserName
            subredditSearchQuery.value = if (isUserName) "" else typedName

            binding.buttonAddUser.isVisible = typedName.isNotBlank()
            binding.buttonAddUser.text = getString(R.string.multireddit_add_as_user, typedName)
        }
    }

    // The add field without its u/ or r/ prefix.
    private fun typedMemberName(): String {
        return binding.inputAddMember.text.toString().trim()
            .removePrefixes(USER_PREFIXES + SUBREDDIT_PREFIXES)
            .trim()
    }

    private fun addSubreddit(name: String) {
        if (subredditMembers.none { it.equals(name, ignoreCase = true) }) viewModel.addSubreddit(name)
        clearAddField()
    }

    private fun addUser(name: String) {
        if (userMembers.none { it.equals(name, ignoreCase = true) }) viewModel.addUser(name)
        clearAddField()
    }

    private fun clearAddField() {
        binding.inputAddMember.text?.clear()
    }

    private fun onSaveClicked() {
        val name = binding.inputName.text().orEmpty()
        val id = multiredditId
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun String.removePrefixes(prefixes: List<String>): String {
        val matchingPrefix = prefixes.firstOrNull { startsWith(it, ignoreCase = true) }
        return if (matchingPrefix == null) this else substring(matchingPrefix.length)
    }

    companion object {
        private const val ARG_ID = "multireddit_id"
        private const val TAG = "MultiredditEditDialogFragment"

        // Longest first, so "/u/" is not left as "/" after stripping "u/".
        private val USER_PREFIXES = listOf("/u/", "u/")
        private val SUBREDDIT_PREFIXES = listOf("/r/", "r/")

        fun show(fragmentManager: FragmentManager, id: Long? = null) {
            MultiredditEditDialogFragment().apply {
                arguments = bundleOf(ARG_ID to (id ?: -1L))
            }.show(fragmentManager, TAG)
        }
    }
}
