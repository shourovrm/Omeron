package com.omeron.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
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
import com.omeron.ui.profilemanager.ProfileManagerDialogFragment
import com.omeron.util.extension.clearCommentListener
import com.omeron.util.extension.clearHistoryRemoveListener
import com.omeron.util.extension.clearSavedRemoveListener
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.hideSoftKeyboard
import com.omeron.util.extension.latest
import com.omeron.util.extension.scrollToTop
import com.omeron.util.extension.setCommentListener
import com.omeron.util.extension.setHistoryRemoveListener
import com.omeron.util.extension.setSavedRemoveListener
import com.omeron.util.extension.showSoftKeyboard
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override val viewModel: ProfileViewModel by hiltNavGraphViewModels(R.id.profile)

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
        binding.backCard.setOnClickListener { onBackPressed() }
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
        binding.switchButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.currentProfile.latest?.let {
                    ProfileManagerDialogFragment.show(parentFragmentManager, it)
                }
            }
        }
    }

    private fun showSearchInput(show: Boolean) {
        binding.title.isVisible = !show
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

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedProfile
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect {
                    binding.profile = it
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

    override fun onBackPressed() {
        if (binding.searchInput.isVisible) {
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
        _binding = null
    }
}
