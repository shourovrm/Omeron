package com.omeron.ui.you

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.omeron.NavigationGraphDirections
import com.omeron.R
import com.omeron.UiViewModel
import com.omeron.data.model.preferences.ProfileTab
import com.omeron.databinding.FragmentYouBinding
import com.omeron.databinding.ItemYouRowBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.profilemanager.ProfileSwitcherDialogFragment
import com.omeron.util.extension.keepClearOfBottomNavigation
import com.omeron.util.extension.latest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class YouFragment : BaseFragment() {

    private var _binding: FragmentYouBinding? = null
    private val binding get() = _binding!!

    override val viewModel: YouViewModel by viewModels()
    private val uiViewModel: UiViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentYouBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        keepClearOfBottomNavigation(binding.scroll, uiViewModel.bottomNavigationHeight)
        initProfileRow()
        initRows()
        bindProfile()
    }

    private fun initProfileRow() {
        binding.profileRow.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.currentProfile.latest?.let {
                    ProfileSwitcherDialogFragment.show(parentFragmentManager, it)
                }
            }
        }
    }

    private fun initRows() {
        val navController = findNavController()

        addRow(
            binding.rowsLibrary, R.drawable.ic_bookmark_outline, R.string.profile_tab_saved_posts,
            count = viewModel.savedPostsCount
        ) { openProfile(ProfileTab.SAVED_POSTS) }

        addRow(
            binding.rowsLibrary, R.drawable.ic_comments, R.string.profile_tab_saved_comments,
            count = viewModel.savedCommentsCount
        ) { openProfile(ProfileTab.SAVED_COMMENTS) }

        addRow(
            binding.rowsLibrary, R.drawable.ic_history, R.string.profile_tab_history,
            count = viewModel.historyCount
        ) { openProfile(ProfileTab.HISTORY) }

        addRow(
            binding.rowsLibrary, R.drawable.ic_communities, R.string.drawer_section_communities,
            supportingText = R.string.you_communities_manage
        ) { navController.navigate(NavigationGraphDirections.openManageCommunities()) }

        addRow(
            binding.rowsLibrary, R.drawable.ic_multireddit, R.string.tab_home_multis,
            supportingText = R.string.you_multis_manage
        ) { navController.navigate(NavigationGraphDirections.openManageMultireddits()) }

        addRow(
            binding.rowsApp, R.drawable.ic_outline_settings, R.string.navigation_label_settings
        ) { navController.navigate(R.id.settings) }

        addRow(
            binding.rowsApp, R.drawable.ic_omeron, R.string.preference_about
        ) { navController.navigate(R.id.aboutFragment) }
    }

    private fun openProfile(tab: ProfileTab) {
        findNavController().navigate(R.id.profile, bundleOf(ARGUMENT_INITIAL_TAB to tab.ordinal))
    }

    /** Adds one list item; [count] shows as the trailing number once it is known. */
    private fun addRow(
        container: ViewGroup,
        @DrawableRes icon: Int,
        @StringRes label: Int,
        @StringRes supportingText: Int? = null,
        count: Flow<Int>? = null,
        onClick: () -> Unit
    ) {
        val row = ItemYouRowBinding.inflate(layoutInflater, container, false)
        row.rowIcon.setImageResource(icon)
        row.rowLabel.setText(label)
        if (supportingText != null) {
            row.rowSupporting.setText(supportingText)
            row.rowSupporting.isVisible = true
            row.root.minimumHeight = resources.getDimensionPixelSize(R.dimen.list_item_two_line)
        }
        row.root.setOnClickListener { onClick() }
        container.addView(row.root)

        if (count != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                count.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                    .collect {
                        row.rowCount.text = it.toString()
                        row.rowCount.isVisible = true
                    }
            }
        }
    }

    private fun bindProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedProfile
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect {
                    binding.profileName.text = it.name
                    binding.profileImage.setText(it.name)
                }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        // Matches the argument name in navigation/profile.xml.
        const val ARGUMENT_INITIAL_TAB = "initialTab"
    }
}
