package com.omeron.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.omeron.R
import com.omeron.UiViewModel
import com.omeron.databinding.FragmentProfileBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.common.adapter.FragmentAdapter
import com.omeron.ui.profilemanager.ProfileManagerDialogFragment
import com.omeron.util.extension.clearCommentListener
import com.omeron.util.extension.clearHistoryRemoveListener
import com.omeron.util.extension.clearNavigationListener
import com.omeron.util.extension.getListContent
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.hideSoftKeyboard
import com.omeron.util.extension.latest
import com.omeron.util.extension.scrollToTop
import com.omeron.util.extension.setCommentListener
import com.omeron.util.extension.setHistoryRemoveListener
import com.omeron.util.extension.setNavigationListener
import com.omeron.util.extension.showSoftKeyboard
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    override val viewModel: ProfileViewModel by hiltNavGraphViewModels(R.id.profile)
    private val uiViewModel: UiViewModel by activityViewModels()

    // Workaround for MotionLayout that prevents bottom navigation from being hidden on scroll
    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy > 0 && uiViewModel.navigationVisibility.value) {
                uiViewModel.setNavigationVisibility(false)
            } else if (dy < 0 && !uiViewModel.navigationVisibility.value) {
                uiViewModel.setNavigationVisibility(true)
            }
        }
    }

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
        initViewPager()
        bindViewModel()

        viewModel.layoutState?.let { binding.layoutRoot.jumpToState(it) }
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

        setNavigationListener { showNavigation ->
            uiViewModel.setNavigationVisibility(showNavigation)
        }
    }

    private fun initAppBar() {
        binding.usersCard.setOnClickListener {
            lifecycleScope.launch {
                viewModel.currentProfile.latest?.let {
                    ProfileManagerDialogFragment.show(parentFragmentManager, it)
                }
            }
        }

        binding.clearHistoryCard.setOnClickListener { confirmClearHistory() }

        binding.searchCard.setOnClickListener { showSearchInput(true) }
        binding.cancelCard.setOnClickListener {
            showSearchInput(false)
            binding.searchInput.clear()
        }
        binding.searchInput.apply {
            addTarget(binding.profileImage)
            addTarget(binding.profileName)
            addTarget(binding.usersCard)
            addTarget(binding.searchCard)
            addTarget(binding.cancelCard)
            doOnTextChanged { text, _, _, _ ->
                viewModel.setSearchQuery(text.toString())
            }
            setSearchActionListener {
                binding.searchInput.hideSoftKeyboard()
            }
        }
    }

    private fun showSearchInput(show: Boolean) {
        setAppBarItemVisible(binding.profileImage, !show)
        setAppBarItemVisible(binding.profileName, !show)
        setAppBarItemVisible(binding.usersCard, !show)
        setAppBarItemVisible(binding.searchCard, !show)
        setAppBarItemVisible(binding.cancelCard, show)
        setAppBarItemVisible(
            binding.clearHistoryCard,
            !show && viewModel.page.value == HISTORY_TAB_INDEX
        )
        setAppBarItemVisible(binding.searchInput, show)
        if (show) {
            binding.searchInput.isFocusableInTouchMode = true
            binding.searchInput.requestFocus()
            binding.searchInput.showSoftKeyboard()
        } else {
            binding.searchInput.hideSoftKeyboard()
        }
    }

    // MotionLayout reapplies each ConstraintSet's visibility, so plain
    // setVisibility on children gets overridden; update the scene states too.
    private fun setAppBarItemVisible(view: View, visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        view.visibility = visibility
        binding.layoutRoot.getConstraintSet(R.id.expanded)?.setVisibility(view.id, visibility)
        binding.layoutRoot.getConstraintSet(R.id.collapsed)?.setVisibility(view.id, visibility)
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_clear_history_title)
            .setMessage(R.string.dialog_clear_history_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> viewModel.clearHistory() }
            .setNegativeButton(R.string.dialog_no) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun initViewPager() {
        val fragments = listOf(
            FragmentAdapter.Page(R.string.tab_profile_posts, ProfileSavedPostsFragment::class.java),
            FragmentAdapter.Page(R.string.tab_profile_comments, ProfileSavedCommentsFragment::class.java),
            FragmentAdapter.Page(R.string.tab_profile_history, ProfileHistoryFragment::class.java)
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

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedProfile
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect {
                    binding.profile = it
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.page
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
                .collect { page ->
                    registerScrollListener(page)
                    if (!binding.searchInput.isVisible) {
                        setAppBarItemVisible(binding.clearHistoryCard, page == HISTORY_TAB_INDEX)
                    }
                }
        }
    }

    override fun onBackPressed() {
        if (binding.searchInput.isVisible) {
            showSearchInput(false)
            binding.searchInput.clear()
        } else {
            super.onBackPressed()
        }
    }

    private fun registerScrollListener(position: Int) {
        binding.viewPager.getListContent(position)?.let {
            it.listContent.run {
                clearOnScrollListeners()
                addOnScrollListener(onScrollListener)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.viewPager.adapter?.let {
            for (i in 0 until it.itemCount) {
                binding.viewPager.getListContent(i)?.listContent?.clearOnScrollListeners()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        clearCommentListener()
        clearHistoryRemoveListener()
        clearNavigationListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Save header state to restore it in case of fragment recreation
        viewModel.layoutState = binding.layoutRoot.currentState

        _binding = null
    }

    companion object {
        private const val HISTORY_TAB_INDEX = 2
    }
}
