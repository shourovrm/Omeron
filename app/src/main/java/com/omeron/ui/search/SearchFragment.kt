package com.omeron.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.data.model.preferences.PostLayout
import com.omeron.databinding.FragmentSearchBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.common.adapter.FragmentAdapter
import com.omeron.UiViewModel
import com.omeron.ui.sort.SortFragment
import com.omeron.util.SearchUtil
import com.omeron.util.extension.clearNavigationListener
import com.omeron.util.extension.clearSortingListener
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.iconRes
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.scrollToTop
import com.omeron.util.extension.setNavigationListener
import com.omeron.util.extension.setSortingListener
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : BaseFragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    override val viewModel: SearchViewModel by hiltNavGraphViewModels(R.id.search)

    private val uiViewModel: UiViewModel by activityViewModels()

    private val args: SearchFragmentArgs by navArgs()

    // ponytail: search's post/subreddit/user tabs share this one appbar, so the toggle just
    // flips the global default here; SearchPostFragment applies it to its own adapter.
    private var currentPostLayout: PostLayout = PostLayout.CARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val query = args.query

            viewModel.setQuery(query)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val query = viewModel.query.value.takeIf { it.isNotBlank() } ?: args.query

        binding.appBar.searchInput.setText(query)

        initResultListener()
        initAppBar()
        initViewPager()
        bindViewModel()

        lifecycleScope.launch {
            delay(250)
            // Bottom-nav tap arrives with a blank query; open the input right away instead of
            // showing an empty results screen.
            showSearchInput(query.isBlank())
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.query.collect { query ->
                    query.takeIf { it.isNotBlank() }?.let {
                        binding.appBar.label.text = query
                    }
                }
            }

            launch {
                viewModel.sorting.collect {
                    binding.appBar.sortIcon.setSorting(it)
                }
            }

            launch {
                viewModel.postLayout.collect { layout ->
                    currentPostLayout = layout
                    binding.appBar.layoutToggleCard.setIcon(layout.iconRes())
                }
            }
        }
    }

    private fun initViewPager() {
        val fragments = listOf(
            FragmentAdapter.Page(R.string.tab_search_post, SearchPostFragment::class.java),
            FragmentAdapter.Page(
                R.string.tab_search_subreddit,
                SearchSubredditFragment::class.java
            ),
            FragmentAdapter.Page(R.string.tab_search_user, SearchUserFragment::class.java)
        )

        val fragmentAdapter = FragmentAdapter(this, fragments)

        binding.viewPager.apply {
            adapter = fragmentAdapter
            offscreenPageLimit = 2
            getRecyclerView()?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
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
        with(binding.appBar) {
            label.setOnClickListener { showSearchInput(true) }
            root.setOnClickListener { showSearchInput(true) }
            searchInput.apply {
                addTarget(backCard)
                addTarget(label)
                addTarget(sortCard)
                addTarget(sortIcon)
                addTarget(layoutToggleCard)
                addTarget(cancelCard)
                setSearchActionListener {
                    handleSearchAction(it)
                }
            }
            sortCard.setOnClickListener { showSortDialog() }
            layoutToggleCard.setOnClickListener { toggleLayout() }
            backCard.setOnClickListener { onBackPressed() }
            cancelCard.setOnClickListener { showSearchInput(false) }
        }
    }

    private fun initResultListener() {
        setSortingListener { sorting -> sorting?.let { viewModel.setSorting(it) } }

        setNavigationListener { showNavigation ->
            uiViewModel.setNavigationVisibility(showNavigation)
        }
    }

    private fun showSearchInput(show: Boolean) {
        binding.appBar.searchInput.apply {
            show(binding.appBar.root, show) {
                with(binding.appBar) {
                    backCard.isVisible = !show
                    label.isVisible = !show
                    sortCard.isVisible = !show
                    sortIcon.isVisible = !show
                    layoutToggleCard.isVisible = !show
                    cancelCard.isVisible = show
                }
            }
            setSelection(text?.length ?: 0)
        }
    }

    private fun handleSearchAction(query: String) {
        if (SearchUtil.isQueryValid(query)) {
            viewModel.setQuery(query)
            showSearchInput(false)
        }
    }

    private fun showSortDialog() {
        SortFragment.show(
            childFragmentManager,
            viewModel.sorting.value,
            SortFragment.SortType.SEARCH
        )
    }

    private fun toggleLayout() {
        viewModel.setPostLayout(currentPostLayout.next())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearSortingListener()
        clearNavigationListener()
        _binding = null
    }

    companion object {
        const val TAG = "SearchFragment"
    }
}
