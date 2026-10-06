package com.omeron.ui.filmstrip

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.omeron.R
import com.omeron.UiViewModel
import com.omeron.data.model.db.PostEntity
import com.omeron.databinding.FragmentFilmstripViewerBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.mediaviewer.MediaDownloadRequester
import com.omeron.util.DateUtil
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.shareExternalLink
import com.omeron.util.extension.showWithAlpha
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen viewer that pages through the media of the feed it was opened from. It is added
 * on top of the feed's fragment, which stays alive underneath.
 */
@AndroidEntryPoint
class FilmstripViewerFragment : BaseFragment() {

    private var _binding: FragmentFilmstripViewerBinding? = null
    private val binding get() = _binding!!

    override val viewModel: FilmstripViewerViewModel by viewModels()
    private val uiViewModel: UiViewModel by activityViewModels()

    private lateinit var playback: FilmstripPlayback
    private lateinit var frameAdapter: FilmstripFrameAdapter
    private lateinit var detailsBehavior: BottomSheetBehavior<NestedScrollView>

    private val downloadRequester = MediaDownloadRequester(this) { binding.root }

    private var currentFrame: FilmstripFrame? = null

    // False until the pager has been moved to the tapped post; a new view always starts at page 0.
    private var isPagerPlaced = false

    private var isStopped = false
    private var isCoveredByOtherFragment = false

    private var navigationVisibilityBeforeOpen = true
    private var detailsBasePaddingBottom = 0

    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        // The viewer stays resumed when the post page is added on top, so the back stack is the
        // only signal that something now covers it.
        val topFragment = parentFragmentManager.fragments.lastOrNull()
        val isCovered = topFragment != null && topFragment !== this
        if (isCovered != isCoveredByOtherFragment) {
            isCoveredByOtherFragment = isCovered
            applyPlaybackSuspension()
            if (!isCovered) uiViewModel.setNavigationVisibility(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initialPostId = arguments?.getString(KEY_POST_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilmstripViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // After process death the feed this viewer was showing is gone, so there is nothing to page.
        if (!viewModel.hasFeed) {
            parentFragmentManager.popBackStack()
            return
        }

        isPagerPlaced = false
        navigationVisibilityBeforeOpen = uiViewModel.navigationVisibility.value
        uiViewModel.setNavigationVisibility(false)

        initPlayback()
        initPager()
        initOverlay()
        initDetailsSheet()
        bindViewModel()

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)
    }

    override fun applyInsets(view: View) {
        detailsBasePaddingBottom = binding.detailsContent.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topContainer.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.bottomInsetSpace.updateLayoutParams { height = bars.bottom }
            binding.detailsContent.updatePadding(bottom = detailsBasePaddingBottom + bars.bottom)
            windowInsets
        }
    }

    override fun onStart() {
        super.onStart()
        isStopped = false
        if (::playback.isInitialized) applyPlaybackSuspension()
    }

    override fun onStop() {
        super.onStop()
        isStopped = true
        if (::playback.isInitialized) applyPlaybackSuspension()
    }

    override fun onBackPressed() {
        val isDetailsOpen = ::detailsBehavior.isInitialized &&
            detailsBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        if (isDetailsOpen) {
            collapseDetails()
        } else {
            close()
        }
    }

    override fun onDestroyView() {
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
        if (::playback.isInitialized) playback.release()
        uiViewModel.setNavigationVisibility(navigationVisibilityBeforeOpen)
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Rotation recreates the viewer; only a real close ends the feed session.
        if (activity?.isChangingConfigurations != true) viewModel.endSession()
    }

    private fun close() {
        parentFragmentManager.popBackStack()
    }

    private fun initPlayback() {
        playback = FilmstripPlayback(
            requireContext(),
            onAudioAvailable = { hasAudio ->
                _binding?.let { it.buttonMute.isVisible = hasAudio && currentFrame?.isVideo == true }
            },
            onPlayerChanged = { player ->
                _binding?.videoSeek?.let { videoSeek ->
                    videoSeek.setPlayer(player)
                    if (player != null) videoSeek.show() else videoSeek.hide()
                }
            }
        )
    }

    private fun initPager() {
        frameAdapter = FilmstripFrameAdapter(
            playback,
            onFrameClick = { toggleOverlay() },
            onRetryResolution = { frame -> viewModel.retryResolution(frame.post.id) }
        )

        binding.viewPager.apply {
            adapter = frameAdapter
            // The view model remembers the page; the pager's own saved state would restore it
            // before the frames exist and then fight the placement below.
            isSaveEnabled = false
            getRecyclerView()?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            getRecyclerView()?.isNestedScrollingEnabled = false
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    frameAdapter.currentList.getOrNull(position)?.let(::selectFrame)
                }
            })
        }
    }

    private fun initOverlay() {
        binding.run {
            overlay.isVisible = viewModel.isOverlayVisible

            buttonClose.setOnClickListener { close() }
            railComments.setOnClickListener { openPost() }
            buttonSave.setOnClickListener { toggleSave() }
            buttonMute.setOnCheckedChangeListener { _, isMuted -> viewModel.setMuted(isMuted) }
        }
        initInfoGestures()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initInfoGestures() {
        val swipeThreshold = ViewConfiguration.get(requireContext()).scaledTouchSlop * 3
        var hasExpandedBySwipe = false

        val gestureDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent) = true

                override fun onScroll(
                    firstEvent: MotionEvent,
                    currentEvent: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    val upwardTravel = firstEvent.y - currentEvent.y
                    val isVertical = abs(distanceY) > abs(distanceX)
                    if (!hasExpandedBySwipe && isVertical && upwardTravel > swipeThreshold) {
                        hasExpandedBySwipe = true
                        expandDetails()
                    }
                    return hasExpandedBySwipe
                }
            }
        )

        // The tap is left to the click listener; this only claims the gesture once it is a swipe.
        binding.info.setOnClickListener { expandDetails() }
        binding.info.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)
            if (hasExpandedBySwipe) {
                view.isPressed = false
                val isGestureOver = event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                if (isGestureOver) hasExpandedBySwipe = false
                true
            } else {
                false
            }
        }
    }

    private fun initDetailsSheet() {
        detailsBasePaddingBottom = binding.detailsContent.paddingBottom

        detailsBehavior = BottomSheetBehavior.from(binding.detailsSheet).apply {
            isHideable = true
            skipCollapsed = true
            state = if (viewModel.isDetailsExpanded) {
                BottomSheetBehavior.STATE_EXPANDED
            } else {
                BottomSheetBehavior.STATE_HIDDEN
            }
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(sheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            viewModel.isDetailsExpanded = true
                            // Restoring an open sheet after rotation skips onSlide.
                            binding.detailsDim.isVisible = true
                            binding.detailsDim.alpha = 1F
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            viewModel.isDetailsExpanded = false
                            binding.detailsDim.isVisible = false
                        }
                    }
                }

                override fun onSlide(sheet: View, slideOffset: Float) {
                    binding.detailsDim.isVisible = true
                    binding.detailsDim.alpha = slideOffset.coerceIn(0F, 1F)
                }
            })
        }

        binding.run {
            detailsDim.setOnClickListener { collapseDetails() }
            buttonOpenPost.setOnClickListener { openPost() }
            actionSave.setOnClickListener { toggleSave() }
            actionShare.setOnClickListener { sharePost() }
            actionDownload.setOnClickListener { downloadCurrentMedia() }
            linkSubreddit.setOnClickListener {
                currentFrame?.post?.let { post ->
                    collapseDetails()
                    openSubreddit(post.subredditName)
                }
            }
            linkUser.setOnClickListener {
                currentFrame?.post?.let { post ->
                    collapseDetails()
                    openUser(post.author)
                }
            }
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.frames.collect { frames -> showFrames(frames) }
            }

            launch {
                viewModel.isMuted.collect { isMuted ->
                    playback.isMuted = isMuted
                    binding.buttonMute.isChecked = isMuted
                }
            }
        }
    }

    private fun showFrames(frames: List<FilmstripFrame>) {
        frameAdapter.submitList(frames) {
            // The commit callback can run after the view is gone if the user closes quickly.
            if (_binding == null) return@submitList

            if (!isPagerPlaced && frames.isNotEmpty()) {
                isPagerPlaced = true
                val initialIndex = viewModel.initialFrameIndex(frames)
                binding.viewPager.setCurrentItem(initialIndex, false)
                selectFrame(frames[initialIndex])
            } else {
                // Same page, but its frame may have changed (resolved, or gained gallery frames).
                frames.firstOrNull { it.id == viewModel.currentFrameId }?.let(::bindOverlay)
            }
        }
    }

    private fun selectFrame(frame: FilmstripFrame) {
        viewModel.onFrameSelected(frame)
        frameAdapter.setActiveFrameId(frame.id)

        // A video's tap means pause, so the overlay could never be brought back from a video.
        if (frame.isVideo && !viewModel.isOverlayVisible) setOverlayVisible(true)

        bindOverlay(frame)
    }

    private fun bindOverlay(frame: FilmstripFrame) {
        currentFrame = frame
        val post = frame.post
        val age = DateUtil.getTimeDifference(requireContext(), post.created, false)

        binding.run {
            avatarCommunity.setText(post.subredditName)
            textSubreddit.text = post.subreddit
            textAuthorAge.text = getString(R.string.filmstrip_info_meta, post.author, age)
            textTitle.text = post.title

            segments.isVisible = frame.isGalleryFrame
            textPosition.isVisible = frame.isGalleryFrame
            segments.setPosition(frame.indexInPost, frame.framesInPost)
            textPosition.text = getString(
                R.string.filmstrip_position,
                frame.indexInPost + 1,
                frame.framesInPost
            )

            textScore.text = post.score
            railScore.contentDescription = getString(R.string.filmstrip_score_description, post.score)
            textComments.text = post.commentsNumber
            railComments.contentDescription = getString(
                R.string.filmstrip_comments_description,
                post.commentsNumber
            )

            // The mute button reappears when the player reports an audio track.
            if (!frame.isVideo) buttonMute.isVisible = false
        }

        bindSavedState(post)
        bindDetails(frame, age)
    }

    private fun bindDetails(frame: FilmstripFrame, age: String) {
        val post = frame.post
        val body = post.previewText?.toString()?.trim()

        binding.run {
            detailsAvatar.setText(post.subredditName)
            detailsSubreddit.text = post.subreddit
            detailsMeta.text = getString(R.string.filmstrip_details_meta, post.author, age, post.domain)
            detailsTitle.text = post.title
            detailsBody.text = body
            detailsBody.isVisible = !body.isNullOrEmpty()

            buttonOpenPost.text = if (post.commentsNumber == SINGLE_COMMENT) {
                getString(R.string.filmstrip_open_post_single)
            } else {
                getString(R.string.filmstrip_open_post, post.commentsNumber)
            }

            actionDownload.isEnabled = frame.status == FrameStatus.READY
            actionDownload.alpha = if (actionDownload.isEnabled) 1F else DISABLED_ACTION_ALPHA

            linkSubreddit.text = getString(R.string.filmstrip_go_to_subreddit, post.subredditName)
            linkUser.text = getString(R.string.filmstrip_go_to_user, post.author)
        }
    }

    private fun bindSavedState(post: PostEntity) {
        binding.run {
            buttonSave.setImageResource(
                if (post.saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline
            )
            buttonSave.contentDescription = getString(
                if (post.saved) R.string.post_unsave_description else R.string.post_save_description
            )

            actionSave.setCompoundDrawablesWithIntrinsicBounds(
                0,
                if (post.saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline,
                0,
                0
            )
            actionSave.setText(
                if (post.saved) R.string.filmstrip_action_saved else R.string.filmstrip_action_save
            )
        }
    }

    private fun toggleSave() {
        val post = currentFrame?.post ?: return
        // The view model reads post.saved synchronously, so it must run before the flip, the
        // same order the list rows use.
        viewModel.toggleSavePost(post)
        post.saved = !post.saved
        bindSavedState(post)
    }

    private fun sharePost() {
        val post = currentFrame?.post ?: return
        shareExternalLink("https://www.reddit.com${post.permalink}", post.title)
    }

    private fun downloadCurrentMedia() {
        currentFrame?.media?.let { downloadRequester.request(it) }
    }

    private fun openPost() {
        val post = currentFrame?.post ?: return
        collapseDetails()
        onClick(post)
    }

    private fun toggleOverlay() {
        setOverlayVisible(!viewModel.isOverlayVisible)
    }

    private fun setOverlayVisible(isVisible: Boolean) {
        viewModel.isOverlayVisible = isVisible
        binding.overlay.showWithAlpha(isVisible, OVERLAY_FADE_MILLIS)
    }

    private fun expandDetails() {
        detailsBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun collapseDetails() {
        detailsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun applyPlaybackSuspension() {
        val shouldSuspend = isStopped || isCoveredByOtherFragment
        if (shouldSuspend == playback.isSuspended) return

        if (shouldSuspend) {
            playback.halt()
        } else {
            playback.resume()
            frameAdapter.restartActive()
        }
    }

    companion object {
        const val TAG = "FilmstripViewerFragment"

        private const val KEY_POST_ID = "KEY_POST_ID"
        private const val SINGLE_COMMENT = "1"
        private const val OVERLAY_FADE_MILLIS = 200L
        private const val DISABLED_ACTION_ALPHA = 0.4F

        fun newInstance(postId: String) = FilmstripViewerFragment().apply {
            arguments = bundleOf(KEY_POST_ID to postId)
        }
    }
}
