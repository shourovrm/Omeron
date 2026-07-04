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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.NavigationGraphDirections
import com.omeron.R
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.databinding.FragmentMultiredditsBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.util.extension.applyWindowInsets
import dagger.hilt.android.AndroidEntryPoint
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
        binding.buttonCreateMultireddit.setOnClickListener {
            MultiredditEditDialogFragment.show(childFragmentManager)
        }
    }

    private fun initRecyclerView() {
        multiredditsAdapter = MultiredditsAdapter(
            onClick = { openFeed(it) },
            onLongClick = { showEditDialog(it) },
            onEdit = { showEditDialog(it) },
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

    private fun openFeed(item: MultiredditWithMembers) {
        navigate(NavigationGraphDirections.openMultireddit(item.multireddit.id))
    }

    private fun showEditDialog(item: MultiredditWithMembers) {
        MultiredditEditDialogFragment.show(childFragmentManager, item.multireddit.id)
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
