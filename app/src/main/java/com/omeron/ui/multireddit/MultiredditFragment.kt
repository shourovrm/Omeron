package com.omeron.ui.multireddit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.R
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.preferences.PostLayout
import com.omeron.data.repository.PostListRepository
import com.omeron.databinding.FragmentMultiredditBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.loadstate.NetworkLoadStateAdapter
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.ui.sort.SortFragment
import com.omeron.ui.subscriptions.MultiredditEditDialogFragment
import com.omeron.util.extension.addLoadStateListener
import com.omeron.util.extension.applyWindowInsets
import com.omeron.util.extension.betterSmoothScrollToPosition
import com.omeron.util.extension.clearSortingListener
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.onRefreshFromNetwork
import com.omeron.util.extension.setSortingListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MultiredditFragment : BaseFragment(), PopupMenu.OnMenuItemClickListener {

    private var _binding: FragmentMultiredditBinding? = null
    private val binding get() = _binding!!

    override val viewModel: MultiredditViewModel by viewModels()

    private val args: MultiredditFragmentArgs by navArgs()

    private lateinit var postListAdapter: PostListAdapter

    private var latestMulti: MultiredditWithMembers? = null

    @Inject
    lateinit var repository: PostListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.load(args.multiredditId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMultiredditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initResultListener()
        initAppBar()
        initRecyclerView()
        bindViewModel()
        binding.loadingState.infoRetry.setActionClickListener { postListAdapter.retry() }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.contentPreferences.collect {
                    postListAdapter.contentPreferences = it
                }
            }

            launch {
                viewModel.multireddit.collect { multi ->
                    latestMulti = multi
                    binding.appBar.label.text = multi?.multireddit?.name.orEmpty()
                }
            }

            launch {
                viewModel.sorting.collect {
                    binding.appBar.sortIcon.setSorting(it)
                }
            }

            launch {
                viewModel.postLayout.collect { applyPostLayout(it) }
            }

            launch {
                viewModel.postDataFlow.collectLatest {
                    postListAdapter.submitData(it)
                }
            }
        }
    }

    private fun initRecyclerView() {
        // ponytail: no pull-to-refresh chrome here - mirrors SubredditSearchFragment (a plain
        // pushed list page), not the collapsing-toolbar SubredditFragment which needs it.
        postListAdapter = PostListAdapter(repository, this, this).apply {
            addLoadStateListener(binding.listPost, binding.loadingState) {
                showRetryBar()
            }
        }

        binding.listPost.apply {
            applyWindowInsets(left = false, top = false, right = false)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postListAdapter.withLoadStateHeaderAndFooter(
                header = NetworkLoadStateAdapter { postListAdapter.retry() },
                footer = NetworkLoadStateAdapter { postListAdapter.retry() }
            )
        }

        launchRepeat(Lifecycle.State.STARTED) {
            postListAdapter.onRefreshFromNetwork {
                scrollToTop()
            }
        }
    }

    private fun initAppBar() {
        binding.appBar.apply {
            backCard.setOnClickListener { onBackPressed() }
            sortCard.setOnClickListener { showSortDialog() }
            layoutToggleCard.setOnClickListener { toggleLayout() }
            moreCard.setOnClickListener { showMenu() }
            label.setOnClickListener { scrollToTop() }
        }
    }

    private fun toggleLayout() {
        val next = if (postListAdapter.postLayout == PostLayout.CARD) {
            PostLayout.GALLERY
        } else {
            PostLayout.CARD
        }
        viewModel.setPostLayout(next)
    }

    private fun applyPostLayout(layout: PostLayout) {
        postListAdapter.postLayout = layout
        binding.listPost.layoutManager = when (layout) {
            PostLayout.GALLERY -> StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            PostLayout.CARD -> LinearLayoutManager(requireContext())
        }
        binding.appBar.layoutToggleCard.setIcon(
            if (layout == PostLayout.GALLERY) {
                R.drawable.ic_layout_gallery
            } else {
                R.drawable.ic_layout_card
            }
        )
        binding.appBar.layoutToggleCard.contentDescription = getString(
            if (layout == PostLayout.GALLERY) {
                R.string.layout_toggle_card
            } else {
                R.string.layout_toggle_gallery
            }
        )
    }

    private fun initResultListener() {
        setSortingListener { sorting -> sorting?.let { viewModel.setSorting(it) } }
    }

    private fun scrollToTop() {
        binding.listPost.betterSmoothScrollToPosition(0)
    }

    private fun showSortDialog() {
        SortFragment.show(childFragmentManager, viewModel.sorting.value)
    }

    private fun showRetryBar() {
        if (!binding.loadingState.infoRetry.isVisible) {
            binding.loadingState.infoRetry.show()
        }
    }

    private fun showMenu() {
        PopupMenu(requireContext(), binding.appBar.moreCard)
            .apply {
                menuInflater.inflate(R.menu.multireddit_menu, menu)
                menu.findItem(R.id.toggle_hidden).setTitle(
                    if (latestMulti?.multireddit?.hidden == true) {
                        R.string.multireddit_menu_show
                    } else {
                        R.string.multireddit_menu_hide
                    }
                )
                setOnMenuItemClickListener(this@MultiredditFragment)
            }
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_multireddit_title)
            .setMessage(R.string.dialog_delete_multireddit_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                viewModel.delete()
                onBackPressed()
            }
            .setNegativeButton(R.string.dialog_no) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.edit -> MultiredditEditDialogFragment.show(childFragmentManager, args.multiredditId)
            R.id.toggle_hidden -> viewModel.setHidden(latestMulti?.multireddit?.hidden != true)
            R.id.delete -> confirmDelete()
            else -> return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearSortingListener()
        _binding = null
    }

    companion object {
        const val TAG = "MultiredditFragment"
    }
}
