package com.omeron.ui.profilemanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.omeron.data.model.ProfileItem
import com.omeron.data.model.db.Profile
import com.omeron.databinding.FragmentProfileSwitcherBinding
import com.omeron.util.extension.doAndDismiss
import com.omeron.util.extension.parcelable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// Quick switch between profiles. Renaming and deleting stay in [ProfileManagerDialogFragment],
// which this sheet opens from its "Manage profiles" row.
@AndroidEntryPoint
class ProfileSwitcherDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentProfileSwitcherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileManagerViewModel by viewModels()

    private lateinit var switcherAdapter: ProfileSwitcherAdapter

    private var currentProfile: Profile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentProfile = arguments?.parcelable(KEY_CURRENT_PROFILE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSwitcherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList()
        initActions()
        bindViewModel()
    }

    private fun initList() {
        switcherAdapter = ProfileSwitcherAdapter(currentProfile) { profile ->
            doAndDismiss { viewModel.selectProfile(profile) }
        }
        binding.listProfiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = switcherAdapter
        }
    }

    private fun initActions() {
        binding.addProfileRow.setOnClickListener {
            // The sheet stays open: dismissing it would clear the view model and cancel the save.
            // The new profile shows up in the list and can be tapped to switch to it.
            showAddProfileDialog(requireActivity(), ::existingProfileNames, viewModel::addProfile)
        }
        binding.manageProfilesRow.setOnClickListener {
            currentProfile?.let { ProfileManagerDialogFragment.show(parentFragmentManager, it) }
            dismiss()
        }
    }

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.profiles
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { items ->
                    switcherAdapter.submitList(items.filterIsInstance<ProfileItem.UserProfile>())
                }
        }
    }

    private fun existingProfileNames(): List<String> {
        return switcherAdapter.currentList.map { it.profile.name }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ProfileSwitcherDialogFragment"

        private const val KEY_CURRENT_PROFILE = "KEY_PROFILE"

        fun show(fragmentManager: FragmentManager, currentProfile: Profile) {
            ProfileSwitcherDialogFragment().apply {
                arguments = bundleOf(KEY_CURRENT_PROFILE to currentProfile)
            }.show(fragmentManager, TAG)
        }
    }
}
