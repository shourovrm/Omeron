package com.omeron.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.UiViewModel
import com.omeron.data.model.preferences.PostLayout
import com.omeron.databinding.FragmentSearchBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.common.adapter.FragmentAdapter
import com.omeron.ui.sort.SortFragment
import com.omeron.util.SearchUtil
import com.omeron.util.extension.applyWindowInsets
import com.omeron.util.extension.clearNavigationListener
import com.omeron.util.extension.clearSortingListener
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.hideSoftKeyboard
import com.omeron.util.extension.iconRes
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.scrollToTop
import com.omeron.util.extension.setNavigationListener
import com.omeron.util.extension.setSortingListener
import com.omeron.util.extension.showSoftKeyboard
import com.omeron.util.extension.toggleDescriptionRes
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : BaseFragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    override val viewModel: SearchViewModel by hiltNavGraphViewModels(R.id.search)

    private val uiViewModel: UiViewModel by activityViewModels()

    private val args: SearchFragmentArgs by navArgs()

    private lateinit var suggestionAdapter: SearchSuggestionAdapter

    // ponytail: search's post/subreddit/user tabs share this one sort row, so the toggle just
    // flips the global default here; SearchPostFragment applies it to its own adapter.
    private var currentPostLayout: PostLayout = PostLayout.CARD

    // The query this screen last put in the field. A different query in the view model was set
    // from somewhere else; while it is the same, the field keeps what the user is typing.
    private var lastShownQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a restart the view model gets the query back from its saved state, and the
        // arguments would only bring back the query the screen was first opened with.
        if (savedInstanceState == null) {
            viewModel.setScope(args.subreddit)
            applyArgumentsQuery()
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

        initResultListener()
        initSearchField()
        initSuggestions()
        initViewPager()
        initSortRow()
        bindViewModel()

        lastShownQuery = viewModel.query.value
        binding.inputSearch.setText(lastShownQuery)

        // A visit from the bottom bar is for typing; a restored screen keeps its keyboard state.
        if (savedInstanceState == null && lastShownQuery.isBlank()) {
            binding.inputSearch.post { binding.inputSearch.showSoftKeyboard() }
        }
    }

    private fun applyArgumentsQuery() {
        val query = args.query
        if (SearchUtil.isQueryValid(query)) {
            viewModel.searchFor(query)
        } else {
            viewModel.setQuery(query)
        }
    }

    private fun initSearchField() {
        binding.run {
            inputSearch.doAfterTextChanged { text -> onTypedTextChanged(text?.toString().orEmpty()) }
            inputSearch.setOnEditorActionListener { _, actionId, _ ->
                val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                if (isSearchAction) runSearch(inputSearch.text.toString())
                isSearchAction
            }
            buttonClear.setOnClickListener {
                inputSearch.text.clear()
                inputSearch.showSoftKeyboard()
            }
            chipScope.setOnCloseIconClickListener { viewModel.setScope(null) }
        }
    }

    private fun onTypedTextChanged(typedText: String) {
        binding.buttonClear.isVisible = typedText.isNotEmpty()
        binding.textQueryHint.isVisible = false
        viewModel.setTypedText(typedText)

        // An emptied field leaves the old results behind and goes back to the suggestions.
        if (typedText.isBlank()) viewModel.setQuery("")
    }

    private fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (!SearchUtil.isQueryValid(query)) {
            binding.textQueryHint.text =
                getString(R.string.search_min_length_hint, SearchUtil.QUERY_MIN_LENGTH)
            binding.textQueryHint.isVisible = true
            return
        }

        binding.textQueryHint.isVisible = false
        binding.inputSearch.hideSoftKeyboard()
        viewModel.searchFor(query)
    }

    private fun initSuggestions() {
        suggestionAdapter = SearchSuggestionAdapter(
            onRecentQueryClick = { query ->
                binding.inputSearch.setText(query)
                runSearch(query)
            },
            onRecentQueryRemove = viewModel::removeRecentQuery,
            onCommunityClick = ::openSubreddit
        )

        binding.listSuggestions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = suggestionAdapter
            // The bottom bar floats over the list, so the last row has to scroll clear of it.
            applyWindowInsets(
                left = false,
                top = false,
                right = false,
                extraBottom = resources.getDimensionPixelSize(
                    com.google.android.material.R.dimen.design_bottom_navigation_height
                )
            )
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.query.collect { query -> showQuery(query) }
            }

            launch {
                viewModel.suggestions.collect { suggestionAdapter.submitList(it) }
            }

            launch {
                viewModel.scope.collect { subreddit -> showScope(subreddit) }
            }

            launch {
                viewModel.sorting.collect {
                    binding.sortIcon.setSorting(it)
                }
            }

            launch {
                viewModel.postLayout.collect { layout ->
                    currentPostLayout = layout
                    binding.layoutToggleCard.setIcon(layout.iconRes())
                    binding.layoutToggleCard.contentDescription = getString(layout.toggleDescriptionRes())
                }
            }
        }
    }

    private fun showQuery(query: String) {
        val hasQuery = query.isNotBlank()
        binding.layoutResults.isVisible = hasQuery
        binding.listSuggestions.isVisible = !hasQuery

        if (query != lastShownQuery) {
            lastShownQuery = query
            if (hasQuery && binding.inputSearch.text.toString() != query) {
                binding.inputSearch.setText(query)
            }
        }
    }

    /** A search inside one community lists posts only, so the scope tabs make way. */
    private fun showScope(subreddit: String?) {
        val isScoped = subreddit != null
        binding.run {
            chipScope.isVisible = isScoped
            inputSearch.setHint(
                if (isScoped) R.string.search_hint_in_community else R.string.search_hint_reddit
            )
            if (subreddit != null) {
                chipScope.text = getString(R.string.drawer_community_name, subreddit)
                chipScope.closeIconContentDescription =
                    getString(R.string.search_scope_remove_description, subreddit)
                viewPager.setCurrentItem(POSTS_TAB_POSITION, false)
            }
            tabs.isVisible = !isScoped
            viewPager.isUserInputEnabled = !isScoped
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
                // Sorting and layout only apply to posts; communities and users come as a list.
                binding.rowSort.isVisible = tab?.position == POSTS_TAB_POSITION
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

    private fun initSortRow() {
        binding.sortCard.setOnClickListener { showSortDialog() }
        binding.layoutToggleCard.setOnClickListener { toggleLayout() }
    }

    private fun initResultListener() {
        setSortingListener { sorting -> sorting?.let { viewModel.setSorting(it) } }

        setNavigationListener { showNavigation ->
            uiViewModel.setNavigationVisibility(showNavigation)
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

        private const val POSTS_TAB_POSITION = 0
    }
}
