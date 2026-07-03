package com.omeron.ui.subscriptions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.omeron.databinding.FragmentSubredditsBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.util.extension.applyWindowInsets
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SubredditsFragment : BaseFragment() {

    private var _binding: FragmentSubredditsBinding? = null
    private val binding get() = _binding!!

    override val viewModel: SubscriptionsViewModel by activityViewModels()

    private lateinit var subscriptionsAdapter: SubscriptionsAdapter
    private lateinit var followedUsersAdapter: FollowedUsersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubredditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        bindViewModel()
    }

    private fun initRecyclerView() {
        subscriptionsAdapter = SubscriptionsAdapter(
            listener = { openSubreddit(it) },
            onToggleHidden = { viewModel.toggleSubscriptionHidden(it) }
        )
        followedUsersAdapter = FollowedUsersAdapter(
            onClick = { openUser(it) },
            onToggleHidden = { viewModel.toggleUserHidden(it) },
            onUnfollow = { viewModel.unfollowUser(it) }
        )

        binding.listSubreddits.apply {
            applyWindowInsets(left = false, top = false, right = false)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ConcatAdapter(subscriptionsAdapter, followedUsersAdapter)
        }
    }

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredSubscriptions
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { subscriptions ->
                    subscriptionsAdapter.submitList(subscriptions)
                    updateEmptyState()
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.followedUsers
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { users ->
                    followedUsersAdapter.submitUsers(users)
                    updateEmptyState()
                }
        }
    }

    // ponytail: empty view reacts to the unfiltered lists only, ignoring an in-progress
    // search query (search results emptiness is handled by the search fragment itself).
    private fun updateEmptyState() {
        val isEmpty = subscriptionsAdapter.itemCount == 0 && followedUsersAdapter.itemCount == 0
        val show = isEmpty && viewModel.searchQuery.value.isBlank()
        binding.emptyData.isVisible = show
        binding.textEmptyData.isVisible = show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
