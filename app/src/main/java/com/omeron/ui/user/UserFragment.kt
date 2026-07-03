package com.omeron.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import coil.size.Precision
import coil.size.Scale
import com.omeron.R
import com.omeron.data.model.Resource
import com.omeron.data.model.User
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.PostLayout
import com.omeron.databinding.FragmentUserBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.common.adapter.FragmentAdapter
import com.omeron.ui.postmenu.PostMenuFragment
import com.omeron.ui.sort.SortFragment
import com.omeron.util.extension.clearCommentListener
import com.omeron.util.extension.clearSortingListener
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.scrollToTop
import com.omeron.util.extension.setCommentListener
import com.omeron.util.extension.setSortingListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserFragment : BaseFragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    override val viewModel: UserViewModel by hiltNavGraphViewModels(R.id.user)

    private val args: UserFragmentArgs by navArgs()

    // ponytail: the submitted/comments tabs share this appbar; the toggle only affects the
    // submitted-posts tab, UserPostFragment applies it to its own adapter.
    private var currentPostLayout: PostLayout = PostLayout.CARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setUser(args.user.removeSuffix("/"))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initResultListener()
        initAppBar()
        initViewPager()
        bindViewModel()
        binding.infoRetry.setActionClickListener { retry() }

        viewModel.layoutState?.let { binding.layoutRoot.jumpToState(it) }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.user.collect { user ->
                    user.takeIf { it.isNotBlank() }?.let {
                        viewModel.loadUserInfo(false)
                    }
                }
            }

            launch {
                viewModel.sorting.collect {
                    binding.sortIcon.setSorting(it)
                }
            }

            launch {
                viewModel.postLayout.collect { layout ->
                    currentPostLayout = layout
                    binding.layoutToggleCard.setIcon(
                        if (layout == PostLayout.GALLERY) {
                            R.drawable.ic_layout_gallery
                        } else {
                            R.drawable.ic_layout_card
                        }
                    )
                }
            }

            launch {
                viewModel.isFollowed.collect { followed ->
                    binding.followCard.setIcon(
                        if (followed) R.drawable.ic_person_remove else R.drawable.ic_person_add
                    )
                }
            }

            launch {
                viewModel.about.collect {
                    when (it) {
                        is Resource.Success -> bindInfo(it.data)
                        is Resource.Error -> handleError(it.code)
                        is Resource.Loading -> {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private fun initViewPager() {
        val fragments = listOf(
            FragmentAdapter.Page(R.string.tab_user_submitted, UserPostFragment::class.java),
            FragmentAdapter.Page(R.string.tab_user_comments, UserCommentFragment::class.java)
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

    private fun initAppBar() {
        with(binding) {
            sortCard.setOnClickListener { showSortDialog() }
            layoutToggleCard.setOnClickListener { toggleLayout() }
            backCard.setOnClickListener { onBackPressed() }
            followCard.setOnClickListener { viewModel.toggleFollow() }
        }
    }

    private fun toggleLayout() {
        val next = if (currentPostLayout == PostLayout.CARD) PostLayout.GALLERY else PostLayout.CARD
        viewModel.setPostLayout(next)
    }

    private fun initResultListener() {
        setSortingListener { sorting ->
            sorting?.let { viewModel.setSorting(sorting) }
        }
        setCommentListener { comment -> comment?.let { viewModel.toggleSaveComment(it) } }
    }

    private fun bindInfo(user: User) {
        if (user.isSuspended) {
            showUnauthorizedDialog()
            return
        }

        with(user) {
            binding.user = this

            binding.userImage.load(icon) {
                crossfade(true)
                scale(Scale.FILL)
                precision(Precision.AUTOMATIC)
            }
        }
    }

    private fun handleError(code: Int?) {
        when (code) {
            403 -> showUnauthorizedDialog()
            404 -> showNotFoundDialog()
            else -> showRetryBar()
        }
    }

    private fun retry() {
        if (viewModel.about.value is Resource.Error) {
            viewModel.loadUserInfo(true)
        }
    }

    private fun showRetryBar() {
        if (!binding.infoRetry.isVisible) {
            binding.infoRetry.show()
        }
    }

    private fun showSortDialog() {
        SortFragment.show(childFragmentManager, viewModel.sorting.value)
    }

    private fun showNotFoundDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_user_not_found_title)
            .setMessage(R.string.dialog_user_not_found_body)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> onBackPressed() }
            .setCancelable(false)
            .show()
    }

    private fun showUnauthorizedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_user_unauthorized_title)
            .setMessage(R.string.dialog_user_unauthorized_body)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> onBackPressed() }
            .setCancelable(false)
            .show()
    }

    override fun onLongClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post, PostMenuFragment.MenuType.USER)
    }

    override fun onMenuClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post, PostMenuFragment.MenuType.USER)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Save header state to restore it in case of fragment recreation
        viewModel.layoutState = binding.layoutRoot.currentState

        clearSortingListener()
        clearCommentListener()

        _binding = null
    }
}
