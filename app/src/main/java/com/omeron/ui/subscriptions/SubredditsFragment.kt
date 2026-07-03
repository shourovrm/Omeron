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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.R
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.Subscription
import com.omeron.databinding.FragmentSubredditsBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.common.dialog.MultiredditPickerDialog
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
            onToggleHidden = { viewModel.toggleSubscriptionHidden(it) },
            onLongClick = { showSubscriptionContextMenu(it) }
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

    // ponytail: plain setItems dialog instead of a PopupMenu - one fewer file (no menu xml)
    // and the "Hide"/"Show" label swap is just a string pick, no menu-item title mutation.
    private fun showSubscriptionContextMenu(subscription: Subscription) {
        val hideLabel = if (subscription.hidden) {
            R.string.subscription_menu_show
        } else {
            R.string.subscription_menu_hide
        }
        val actions = arrayOf(
            getString(R.string.add_to_multireddit),
            getString(hideLabel),
            getString(R.string.subreddit_button_unsubscribe)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(subscription.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> MultiredditPickerDialog.show(
                        context = requireContext(),
                        scope = viewLifecycleOwner.lifecycleScope,
                        layoutInflater = layoutInflater,
                        target = subscription.name,
                        type = MultiredditMemberType.SUBREDDIT,
                        getMultireddits = { viewModel.getMultiredditsSnapshot() },
                        addMember = { multiId -> viewModel.addTargetToMultireddit(multiId, subscription.name) },
                        removeMember = { multiId -> viewModel.removeTargetFromMultireddit(multiId, subscription.name) },
                        createMultireddit = { name -> viewModel.createMultiredditWithTarget(name, subscription.name) }
                    )
                    1 -> viewModel.toggleSubscriptionHidden(subscription)
                    2 -> viewModel.unsubscribe(subscription.name)
                }
            }
            .show()
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
