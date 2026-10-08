package com.omeron.ui.subscriptions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.omeron.NavigationGraphDirections
import com.omeron.R
import com.omeron.UiViewModel
import com.omeron.databinding.FragmentManageMultiredditsBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.util.extension.keepClearOfBottomNavigation
import com.omeron.util.extension.launchRepeat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ManageMultiredditsFragment : BaseFragment() {

    private var _binding: FragmentManageMultiredditsBinding? = null
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
        _binding = FragmentManageMultiredditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAppBar()
        initList()
        bindViewModel()
    }

    private fun initAppBar() {
        binding.appBar.run {
            label.setText(R.string.drawer_section_multireddits)
            backCard.setOnClickListener { onBackPressed() }
            actionButton.setText(R.string.manage_new)
            actionButton.setIconResource(R.drawable.ic_add)
            actionButton.isVisible = true
            actionButton.setOnClickListener { MultiredditEditDialogFragment.show(childFragmentManager) }
        }
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
            viewModel.manageMultiredditItems.collect { items ->
                manageAdapter.submitList(items)
                binding.emptyData.isVisible = items.isEmpty()
                binding.textEmptyData.isVisible = items.isEmpty()
            }
        }
    }

    private fun openRow(item: ManageItem) {
        val row = item as? ManageItem.MultiredditRow ?: return
        navigate(NavigationGraphDirections.openMultireddit(row.id))
    }

    private fun toggleHidden(item: ManageItem) {
        val row = item as? ManageItem.MultiredditRow ?: return
        viewModel.setMultiredditHidden(row.id, !row.isHidden)
    }

    private fun showMenu(item: ManageItem) {
        val row = item as? ManageItem.MultiredditRow ?: return
        menus.showMultiredditMenu(row.id, row.name, offerHide = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
