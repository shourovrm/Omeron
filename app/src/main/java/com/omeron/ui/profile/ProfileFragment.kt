package com.omeron.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.omeron.R
import com.omeron.databinding.FragmentProfileBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.common.adapter.FragmentAdapter
import com.omeron.ui.profilemanager.ProfileSwitcherDialogFragment
import com.omeron.ui.you.initialProfileTabPosition
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.model.preferences.ProfileTab
import com.omeron.util.extension.clearCommentListener
import com.omeron.util.extension.clearHistoryRemoveListener
import com.omeron.util.extension.clearSavedRemoveListener
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.hideSoftKeyboard
import com.omeron.util.extension.iconRes
import com.omeron.util.extension.latest
import com.omeron.util.extension.scrollToTop
import com.omeron.util.extension.setCommentListener
import com.omeron.util.extension.setHistoryRemoveListener
import com.omeron.util.extension.setSavedRemoveListener
import com.omeron.util.extension.toggleDescriptionRes
import com.omeron.util.extension.showSoftKeyboard
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override val viewModel: ProfileViewModel by hiltNavGraphViewModels(R.id.profile)

    private val selectionViewModel: ProfileSelectionViewModel by activityViewModels()

    // What the selection bar shows for the tab on screen; the bar's buttons read it when tapped.
    private var selectionBar: SelectionBarState? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initAppBar()
        initProfileRow()
        initViewPager()
        if (savedInstanceState == null) showInitialTab()
        initSelectionBar()
        bindViewModel()
    }

    override fun onStart() {
        super.onStart()
        initResultListener()
    }

    override fun onResume() {
        super.onResume()
        binding.searchInput.text?.firstOrNull()?.let {
            showSearchInput(true)
        }
    }

    private fun initResultListener() {
        setCommentListener { comment -> comment?.let { viewModel.toggleSaveComment(it) } }

        setHistoryRemoveListener { post -> post?.let { viewModel.removeFromHistory(it.id) } }

        setSavedRemoveListener { post -> post?.let { viewModel.toggleSavePost(it) } }
    }

    private fun initAppBar() {
        binding.backCard.setOnClickListener { leaveProfile() }
        binding.layoutToggleCard.setOnClickListener { viewModel.toggleSelectedTabLayout() }
        binding.searchCard.setOnClickListener { showSearchInput(true) }
        binding.cancelCard.setOnClickListener {
            showSearchInput(false)
            binding.searchInput.clear()
        }
        binding.searchInput.apply {
            doOnTextChanged { text, _, _, _ ->
                viewModel.setSearchQuery(text.toString())
            }
            setSearchActionListener {
                binding.searchInput.hideSoftKeyboard()
            }
        }
    }

    private fun initProfileRow() {
        binding.profileRow.setOnClickListener {
            lifecycleScope.launch {
                viewModel.currentProfile.latest?.let {
                    ProfileSwitcherDialogFragment.show(parentFragmentManager, it)
                }
            }
        }
    }

    private fun showSearchInput(show: Boolean) {
        binding.title.isVisible = !show
        binding.layoutToggleCard.isVisible = !show
        binding.searchCard.isVisible = !show
        binding.cancelCard.isVisible = show
        binding.searchInput.isVisible = show
        if (show) {
            binding.searchInput.isFocusableInTouchMode = true
            binding.searchInput.requestFocus()
            binding.searchInput.showSoftKeyboard()
        } else {
            binding.searchInput.hideSoftKeyboard()
        }
    }

    // The icon shows the layout the current tab has now, like the feed's layout button.
    private fun showLayoutToggle(layout: PostLayout) {
        binding.layoutToggleCard.setIcon(layout.iconRes())
        binding.layoutToggleCard.contentDescription = getString(layout.toggleDescriptionRes())
    }

    private fun initViewPager() {
        val fragments = listOf(
            FragmentAdapter.Page(R.string.profile_tab_saved_posts, ProfileSavedPostsFragment::class.java),
            FragmentAdapter.Page(R.string.profile_tab_saved_comments, ProfileSavedCommentsFragment::class.java),
            FragmentAdapter.Page(R.string.profile_tab_history, ProfileHistoryFragment::class.java)
        )

        val fragmentAdapter = FragmentAdapter(this, fragments)

        binding.viewPager.apply {
            adapter = fragmentAdapter
            getRecyclerView()?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    viewModel.setPage(position)
                }
            })
        }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // ignore
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // ignore
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.let { binding.viewPager.scrollToTop(it.position) }
            }
        })

        TabLayoutMediator(binding.tabs, binding.viewPager) { tab, position ->
            tab.setText(fragments[position].title)
        }.attach()
    }

    // Only on first creation: after a rotation the pager restores the tab the user moved to.
    private fun showInitialTab() {
        val requestedPosition = arguments?.getInt(ARGUMENT_INITIAL_TAB, 0) ?: 0
        binding.viewPager.setCurrentItem(initialProfileTabPosition(requestedPosition), false)
    }

    private fun initSelectionBar() {
        binding.selectionLeave.setOnClickListener {
            selectionBar?.let { selectionViewModel.leave(it.tab) }
        }
        binding.selectionSelectAll.setOnClickListener {
            selectionBar?.let { selectionViewModel.toggleSelectAll(it.tab, it.visibleIds) }
        }
        binding.selectionAction.setOnClickListener { removeSelectedRows() }

        viewLifecycleOwner.lifecycleScope.launch {
            val tabAndVisibleIds = viewModel.page.flatMapLatest { position ->
                val tab = ProfileTab.fromPagerPosition(position)
                viewModel.visibleIds(tab).map { visibleIds -> tab to visibleIds }
            }
            combine(selectionViewModel.selection, tabAndVisibleIds) { selection, (tab, visibleIds) ->
                SelectionBarState(
                    tab = tab,
                    isActive = selection.isActive(tab),
                    boxState = selection.boxState(tab, visibleIds),
                    visibleIds = visibleIds,
                    selectedVisibleIds = selection.selectedIds(tab).intersect(visibleIds.toSet())
                )
            }.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { showSelectionBar(it) }
        }

        // Rows that vanish elsewhere (unsaved from a post page, say) leave the selection.
        ProfileTab.values().forEach { tab ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.existingIds(tab)
                    .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                    .collect { selectionViewModel.retainExisting(tab, it) }
            }
        }
    }

    private fun showSelectionBar(state: SelectionBarState) {
        selectionBar = state
        binding.selectionBar.isVisible = state.isActive

        binding.selectionSelectAll.setImageResource(
            when (state.boxState) {
                SelectionBoxState.NONE -> R.drawable.ic_check_box_outline_blank
                SelectionBoxState.SOME -> R.drawable.ic_indeterminate_check_box
                SelectionBoxState.ALL -> R.drawable.ic_check_box
            }
        )
        binding.selectionSelectAll.contentDescription = getString(
            if (state.boxState == SelectionBoxState.ALL) R.string.profile_selection_deselect_all
            else R.string.profile_selection_select_all
        )

        val isHistory = state.tab == ProfileTab.HISTORY
        binding.selectionAction.setImageResource(
            if (isHistory) R.drawable.ic_delete else R.drawable.ic_bookmark_remove
        )
        binding.selectionAction.contentDescription = getString(
            if (isHistory) R.string.profile_selection_remove_history
            else R.string.profile_selection_unsave
        )
        val hasSelection = state.selectedVisibleIds.isNotEmpty()
        binding.selectionAction.isEnabled = hasSelection
        binding.selectionAction.alpha = if (hasSelection) 1f else DISABLED_ACTION_ALPHA
    }

    // Only rows the list shows are removed, so what the user sees ticked is what goes.
    private fun removeSelectedRows() {
        val state = selectionBar ?: return
        if (state.selectedVisibleIds.isEmpty()) return

        selectionViewModel.leave(state.tab)
        viewModel.removeRows(state.tab, state.selectedVisibleIds, ::showUndo)
    }

    private fun showUndo(removed: RemovedRows) {
        // The screen can be gone by the time the rows are removed; there is nothing to anchor to.
        val root = _binding?.root ?: return
        val message = when (removed) {
            is RemovedRows.SavedPosts ->
                resources.getQuantityString(R.plurals.profile_posts_unsaved, removed.count, removed.count)
            is RemovedRows.SavedComments ->
                resources.getQuantityString(R.plurals.profile_comments_unsaved, removed.count, removed.count)
            is RemovedRows.HistoryEntries -> resources.getQuantityString(
                R.plurals.profile_posts_removed_from_history,
                removed.count,
                removed.count
            )
        }
        Snackbar.make(root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.profile_selection_undo) { viewModel.restore(removed) }
            .show()
    }

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedProfile
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect {
                    binding.profile = it
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedTabLayout
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { showLayoutToggle(it) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isRedditLoggedIn
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { isLoggedIn ->
                    binding.redditStatus.setText(
                        if (isLoggedIn) R.string.profile_reddit_logged_in
                        else R.string.profile_reddit_logged_out
                    )
                }
        }
    }

    // The app bar arrow always leaves in one tap, dropping every tab's selection on the way;
    // system Back (below) steps out of selection or search first and stays on the screen.
    private fun leaveProfile() {
        selectionViewModel.leaveAll()
        super.onBackPressed()
    }

    fun handleBackFromTab() {
        onBackPressed()
    }

    override fun onBackPressed() {
        val tabInSelection = selectionBar?.takeIf { it.isActive }?.tab
        if (tabInSelection != null) {
            selectionViewModel.leave(tabInSelection)
        } else if (binding.searchInput.isVisible) {
            showSearchInput(false)
            binding.searchInput.clear()
        } else {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        clearCommentListener()
        clearHistoryRemoveListener()
        clearSavedRemoveListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        selectionBar = null
        _binding = null
    }

    private companion object {
        const val DISABLED_ACTION_ALPHA = 0.38f

        // Matches the argument name in navigation/profile.xml.
        const val ARGUMENT_INITIAL_TAB = "initialTab"
    }
}

/** The selection bar's content: the tab on screen, its mode, and which of its rows are ticked. */
private data class SelectionBarState(
    val tab: ProfileTab,
    val isActive: Boolean,
    val boxState: SelectionBoxState,
    val visibleIds: List<String>,
    val selectedVisibleIds: Set<String>
)
