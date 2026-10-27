package com.omeron.ui.postdetails

import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.widget.doOnTextChanged
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
import androidx.transition.Slide
import androidx.transition.TransitionManager
import com.omeron.R
import com.omeron.data.local.mapper.CommentMapper2
import com.omeron.data.model.Resource
import com.omeron.data.model.db.PostEntity
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.databinding.FragmentPostDetailsBinding
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.di.DispatchersModule.MainImmediateDispatcher
import com.omeron.ui.base.BaseFragment
import com.omeron.data.model.Comment
import com.omeron.ui.commentmenu.CommentMenuFragment
import com.omeron.util.containsIgnoreCase
import com.omeron.ui.postmenu.PostMenuFragment
import com.omeron.ui.common.ElasticDragDismissFrameLayout
import com.omeron.ui.loadstate.ResourceStateAdapter
import com.omeron.ui.sort.SortFragment
import com.omeron.util.extension.applyWindowInsets
import com.omeron.util.extension.betterSmoothScrollToPosition
import com.omeron.util.extension.clearCommentListener
import com.omeron.util.extension.clearSortingListener
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.parcelable
import com.omeron.util.extension.setCommentListener
import com.omeron.util.extension.setSortingListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class PostDetailsFragment : BaseFragment(),
    ElasticDragDismissFrameLayout.ElasticDragDismissCallback {

    private var _binding: FragmentPostDetailsBinding? = null
    private var currentPost: PostEntity? = null
    private var findMatchPositions: List<Int> = emptyList()
    private var findMatchIndex = -1
    private val binding get() = _binding!!

    override val viewModel: PostDetailsViewModel by viewModels()

    override val isPostPage: Boolean = true

    override val borrowedFeedSessionId: Int?
        get() = arguments?.getInt(KEY_FEED_SESSION_ID, NO_FEED_SESSION)?.takeIf { it != NO_FEED_SESSION }

    private val args: PostDetailsFragmentArgs by navArgs()

    private val contentRadius by lazy { resources.getDimension(R.dimen.subreddit_content_radius) }
    private val contentElevation by lazy {
        resources.getDimension(R.dimen.subreddit_content_elevation)
    }

    private var isLegacyNavigation: Boolean = false

    private lateinit var postAdapter: PostAdapter
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var resourceStateAdapter: ResourceStateAdapter

    private val isOnlyPostOpen: Boolean
        get() = parentFragmentManager.fragments.count { it is PostDetailsFragment } == 1

    @Inject
    lateinit var repository: PostListRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var commentMapper: CommentMapper2

    @Inject
    @MainImmediateDispatcher
    lateinit var mainImmediateDispatcher: CoroutineDispatcher

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            preferencesRepository.getContentPreferences().first()
        }

        if (savedInstanceState == null) {
            handleArguments()
        }

        isLegacyNavigation = (args.name == null || args.id == null)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailsBinding.inflate(LayoutInflater.from(context))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutRoot.applyWindowInsets(bottom = false)

        parentFragmentManager.setFragmentResultListener(
            PostMenuFragment.REQUEST_KEY_REFRESH,
            viewLifecycleOwner
        ) { _, _ -> viewModel.loadPost(true) }

        showNavigation(false)

        binding.root.addListener(this)

        initResultListener()
        initAppBar()
        initRecyclerView()
        initFindBar()

        val post = arguments?.parcelable<PostEntity>(KEY_POST_ENTITY)
        post?.let {
            bindPost(it, true)
        }

        bindViewModel()

        binding.singleThreadLayout.setOnClickListener { loadFullDiscussion() }
    }

    private fun initRecyclerView() {
        val contentPreferences = runBlocking {
            preferencesRepository.getContentPreferences().first()
        }

        postAdapter = PostAdapter(contentPreferences, this, this)
        commentAdapter = CommentAdapter(
            requireContext(),
            mainImmediateDispatcher,
            defaultDispatcher,
            repository,
            commentMapper,
            this
        ) {
            CommentMenuFragment.show(childFragmentManager, it, CommentMenuFragment.MenuType.DETAILS)
        }.apply {
            // Wait for data to restore adapter position
            stateRestorationPolicy = PREVENT_WHEN_EMPTY
        }
        resourceStateAdapter = ResourceStateAdapter(::openLogin) { retry() }

        val concatAdapter = ConcatAdapter(postAdapter, resourceStateAdapter, commentAdapter)
        binding.listComments.apply {
            applyWindowInsets(left = false, top = false, right = false)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = concatAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updatePinnedTitle()
                }
            })
        }

        binding.textPinnedTitle.setOnClickListener {
            binding.listComments.betterSmoothScrollToPosition(0)
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                combine(viewModel.permalink, viewModel.sorting) { permalink, _ ->
                    permalink?.let {
                        viewModel.loadPost(false)
                    }
                }.collect()
            }

            launch {
                viewModel.post.collect {
                    when (it) {
                        is Resource.Success -> bindPost(it.data, false)
                        else -> {
                            // ignore
                        }
                    }
                }
            }

            launch {
                viewModel.comments.collect {
                    resourceStateAdapter.resource = it
                    when (it) {
                        is Resource.Success -> commentAdapter.submitList(it.data)
                        else -> {
                            // ignore
                        }
                    }
                }
            }

            launch {
                viewModel.sorting.collect {
                    binding.appBar.sortIcon.setSorting(it)
                }
            }

            launch {
                viewModel.singleThread.collect { isSingleThread ->
                    val transition = Slide(Gravity.TOP).apply {
                        duration = 500
                        addTarget(binding.singleThreadLayout)
                    }
                    TransitionManager.beginDelayedTransition(binding.root, transition)
                    binding.singleThreadLayout.isVisible = isSingleThread
                }
            }

            launch {
                viewModel.savedCommentIds.collect { savedCommentIds ->
                    commentAdapter.savedIds = savedCommentIds
                }
            }
        }
    }

    private fun initAppBar() {
        with(binding.appBar) {
            backCard.setOnClickListener { onBackPressed() }
            sortCard.setOnClickListener { showSortDialog() }
            findCard.setOnClickListener { openFindBar() }
            moreCard.setOnClickListener { showMenu() }
        }
    }

    // Shows the title strip once the title in the post header has scrolled out of view.
    private fun updatePinnedTitle() {
        val list = binding.listComments
        val layoutManager = list.layoutManager as LinearLayoutManager
        val header = layoutManager.findViewByPosition(0)
        val titleView = header?.findViewById<View>(R.id.text_post_title)

        val isTitleScrolledOff = if (titleView == null) {
            true
        } else {
            val titleBounds = Rect()
            titleView.getDrawingRect(titleBounds)
            list.offsetDescendantRectToMyCoords(titleView, titleBounds)
            titleBounds.bottom <= 0
        }
        binding.textPinnedTitle.isVisible = isTitleScrolledOff && currentPost != null
    }

    private fun initFindBar() {
        with(binding) {
            inputFind.doOnTextChanged { text, _, _, _ -> runFind(text?.toString().orEmpty()) }
            inputFind.setOnEditorActionListener { _, _, _ ->
                goToFindMatch(step = 1)
                true
            }
            buttonFindNext.setOnClickListener { goToFindMatch(step = 1) }
            buttonFindPrevious.setOnClickListener { goToFindMatch(step = -1) }
            buttonFindClose.setOnClickListener { closeFindBar() }
        }
    }

    private fun openFindBar() {
        binding.findBar.isVisible = true
        binding.inputFind.requestFocus()
        binding.inputFind.post {
            requireContext().getSystemService<InputMethodManager>()
                ?.showSoftInput(binding.inputFind, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeFindBar() {
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.inputFind.windowToken, 0)
        binding.inputFind.setText("")
        binding.findBar.isVisible = false
    }

    private fun runFind(query: String) {
        postAdapter.findQuery = query
        commentAdapter.findQuery = query

        findMatchPositions = if (query.isBlank()) emptyList() else collectFindMatchPositions(query)
        findMatchIndex = -1

        when {
            query.isBlank() -> binding.textFindCount.text = ""
            findMatchPositions.isEmpty() ->
                binding.textFindCount.setText(R.string.find_no_matches)
            else -> goToFindMatch(step = 1)
        }
    }

    // Adapter positions of the rows that contain the query, in reading order.
    private fun collectFindMatchPositions(query: String): List<Int> {
        val positions = mutableListOf<Int>()

        val post = currentPost
        val isPostMatch = post != null &&
            (post.title.contains(query, ignoreCase = true) ||
                post.selfRedditText.containsIgnoreCase(query))
        if (isPostMatch) positions.add(0)

        val firstCommentPosition = postAdapter.itemCount + resourceStateAdapter.itemCount
        commentAdapter.currentList.forEachIndexed { index, comment ->
            val isCommentMatch = comment is Comment.CommentEntity &&
                comment.body.containsIgnoreCase(query)
            if (isCommentMatch) positions.add(firstCommentPosition + index)
        }
        return positions
    }

    private fun goToFindMatch(step: Int) {
        if (findMatchPositions.isEmpty()) return

        val matchCount = findMatchPositions.size
        findMatchIndex = Math.floorMod(findMatchIndex + step, matchCount)
        binding.textFindCount.text =
            getString(R.string.find_count, findMatchIndex + 1, matchCount)

        val layoutManager = binding.listComments.layoutManager as LinearLayoutManager
        layoutManager.scrollToPositionWithOffset(findMatchPositions[findMatchIndex], 0)
    }

    private fun initResultListener() {
        setSortingListener { sorting ->
            sorting?.let {
                viewModel.setSorting(sorting)
                binding.listComments.betterSmoothScrollToPosition(0)
            }
        }
        setCommentListener { comment -> comment?.let { viewModel.toggleSaveComment(it) } }
    }

    private fun bindPost(post: PostEntity, fromCache: Boolean) {
        currentPost = post
        // The title lives in the pinned strip and the post header; the bar names the subreddit.
        binding.appBar.label.text = post.subreddit
        binding.textPinnedTitle.text = post.title
        postAdapter.setPost(post, fromCache)
        commentAdapter.postEntity = post
        viewModel.insertPostInHistory(post)
    }

    private fun handleArguments() {
        if (args.id != null) {
            val permalink = if (args.name != null) {
                // Full URL
                val stringBuilder = StringBuilder().apply {
                    append("/").append(args.type).append("/").append(args.name).append("/comments/")
                        .append(args.id)
                }

                if (args.title != null && args.comment != null) {
                    stringBuilder.append("/").append(args.title).append("/").append(args.comment)
                    viewModel.setSingleThread(true)
                }

                stringBuilder.toString()
            } else {
                // Shortened URL
                args.id!!
            }

            viewModel.setPermalink(permalink)
        } else {
            if (arguments?.containsKey(KEY_POST_ENTITY) == true) {
                val post = arguments?.parcelable<PostEntity>(KEY_POST_ENTITY)
                post?.let {
                    viewModel.setSorting(it.suggestedSorting)
                    viewModel.setPermalink(it.permalink)
                }
            } else if (arguments?.containsKey(KEY_THREAD_PERMALINK) == true) {
                val threadPermalink = arguments?.getString(KEY_THREAD_PERMALINK)
                threadPermalink?.let {
                    viewModel.setPermalink(it)
                    viewModel.setSingleThread(true)
                }
            }
        }
    }

    private fun loadFullDiscussion() {
        val permalink = viewModel.permalink.value
        permalink?.let {
            val newPermalink = it.removeSuffix("/").substringBeforeLast("/")
            viewModel.setPermalink(newPermalink)
            viewModel.setSingleThread(false)
            viewModel.loadPost(false)
        }
    }

    private fun retry() {
        viewModel.loadPost(true)
    }

    override fun onLoginCompleted() {
        retry()
    }

    private fun showSortDialog() {
        SortFragment.show(
            childFragmentManager,
            viewModel.sorting.value,
            SortFragment.SortType.POST
        )
    }

    private fun showNavigation(show: Boolean) {
        setFragmentResult(REQUEST_KEY_NAVIGATION, bundleOf(BUNDLE_KEY_NAVIGATION to show))
    }

    private fun showMenu() {
        // Same bottom sheet as the list long-press / metrics-row menu, plus Refresh.
        val post = currentPost ?: return
        PostMenuFragment.show(parentFragmentManager, post, PostMenuFragment.MenuType.DETAILS)
    }

    /** Leaves the page as the back button does, for the viewer on top that moved to another post. */
    fun close() {
        onBackPressed()
    }

    override fun onBackPressed() {
        if (_binding != null && binding.findBar.isVisible) {
            closeFindBar()
            return
        }

        if (isOnlyPostOpen) {
            showNavigation(true)
        }

        if (isLegacyNavigation) {
            // Prevent onBackPressed event to be passed to PostDetailsFragment and show bottom nav
            parentFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()

        // Save comment hierarchy
        viewModel.setComments(commentAdapter.currentList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearSortingListener()
        clearCommentListener()
        _binding = null
        commentAdapter.cleanUp()
    }

    override fun onDrag(
        elasticOffset: Float,
        elasticOffsetPixels: Float,
        rawOffset: Float,
        rawOffsetPixels: Float
    ) {
        binding.root.cardElevation = contentElevation * rawOffset
        binding.root.radius = contentRadius * rawOffset
    }

    override fun onDragDismissed() {
        onBackPressed()
    }

    companion object {
        const val TAG = "PostDetailsFragment"

        const val REQUEST_KEY_NAVIGATION = "REQUEST_KEY_NAVIGATION"
        const val BUNDLE_KEY_NAVIGATION = "BUNDLE_KEY_NAVIGATION"

        private const val KEY_POST_ENTITY = "KEY_POST_ENTITY"

        private const val KEY_THREAD_PERMALINK = "KEY_THREAD_PERMALINK"

        private const val KEY_FEED_SESSION_ID = "KEY_FEED_SESSION_ID"

        // Session ids start at 1.
        private const val NO_FEED_SESSION = 0

        /**
         * Page of [post]. With [feedSessionId], the Filmstrip session of the screen it was opened
         * from, the viewer opened on the page's media swipes on to that screen's other posts.
         */
        @JvmStatic
        fun newInstance(post: PostEntity, feedSessionId: Int? = null) = PostDetailsFragment().apply {
            arguments = bundleOf(
                KEY_POST_ENTITY to post,
                KEY_FEED_SESSION_ID to (feedSessionId ?: NO_FEED_SESSION)
            )
        }

        fun newInstance(threadPermalink: String) = PostDetailsFragment().apply {
            arguments = bundleOf(
                KEY_THREAD_PERMALINK to threadPermalink
            )
        }
    }
}
