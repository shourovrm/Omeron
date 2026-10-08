package com.omeron.ui.subscriptions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.omeron.R
import com.omeron.UiViewModel
import com.omeron.databinding.FragmentManageCommunitiesBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.util.extension.keepClearOfBottomNavigation
import com.omeron.util.extension.launchRepeat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ManageCommunitiesFragment : BaseFragment() {

    private var _binding: FragmentManageCommunitiesBinding? = null
    private val binding get() = _binding!!

    override val viewModel: SubscriptionsViewModel by activityViewModels()
    private val uiViewModel: UiViewModel by activityViewModels()

    private lateinit var manageAdapter: ManageAdapter
    private lateinit var menus: SubscriptionMenus

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageCommunitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The ViewModel outlives this page, so a filter typed on an earlier visit would still be
        // applied to a field that now shows empty. A restored field text re-applies itself.
        viewModel.setCommunityFilterQuery("")

        initAppBar()
        initFilter()
        initList()
        bindViewModel()
    }

    private fun initAppBar() {
        binding.appBar.label.setText(R.string.drawer_section_communities)
        binding.appBar.backCard.setOnClickListener { onBackPressed() }
    }

    private fun initFilter() {
        binding.filterInput.doAfterTextChanged { viewModel.setCommunityFilterQuery(it.toString()) }
    }

    private fun initList() {
        menus = SubscriptionMenus(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            layoutInflater = layoutInflater,
            fragmentManager = childFragmentManager,
            viewModel = viewModel
        )
        manageAdapter = ManageAdapter(
            onRowClick = ::openRow,
            onToggleHidden = ::toggleHidden,
            onMoreClick = ::showMenu
        )

        binding.listManage.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = manageAdapter
        }
        keepClearOfBottomNavigation(binding.listManage, uiViewModel.bottomNavigationHeight)
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            viewModel.manageCommunityItems.collect { items ->
                manageAdapter.submitList(items)
                binding.emptyData.isVisible = items.isEmpty()
                binding.textEmptyData.isVisible = items.isEmpty()
            }
        }
    }

    private fun openRow(item: ManageItem) {
        val row = item as? ManageItem.CommunityRow ?: return
        if (row.isUser) openUser(row.name) else openSubreddit(row.name)
    }

    private fun toggleHidden(item: ManageItem) {
        val row = item as? ManageItem.CommunityRow ?: return
        if (row.isUser) {
            viewModel.setUserHidden(row.name, !row.isHidden)
        } else {
            viewModel.setSubscriptionHidden(row.name, !row.isHidden)
        }
    }

    private fun showMenu(item: ManageItem) {
        val row = item as? ManageItem.CommunityRow ?: return
        if (row.isUser) {
            menus.showUserMenu(row.name)
        } else {
            menus.showCommunityMenu(row.name, offerHide = false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
