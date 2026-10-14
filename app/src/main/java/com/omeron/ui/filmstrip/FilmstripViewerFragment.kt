package com.omeron.ui.filmstrip

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.omeron.R
import com.omeron.UiViewModel
import com.omeron.data.model.MediaType
import com.omeron.data.model.db.PostEntity
import com.omeron.databinding.FragmentFilmstripViewerBinding
import com.omeron.ui.base.BaseFragment
import com.omeron.ui.mediaviewer.MediaDownloadRequester
import com.omeron.ui.postdetails.PostDetailsFragment
import com.omeron.util.DateUtil
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.serializable
import com.omeron.util.extension.shareExternalLink
import com.omeron.util.extension.showWithAlpha
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen viewer for every media entry point. Opened on a post session it pages through the
 * media of the feed (or the single post) it was opened from; opened on a link it shows that
 * link's media with a reduced overlay, because there is no post to score, comment on or save.
 * It is added on top of the fragment it was opened from, which stays alive underneath.
 */
@AndroidEntryPoint
class FilmstripViewerFragment : BaseFragment() {

    private var _binding: FragmentFilmstripViewerBinding? = null
    private val binding get() = _binding!!

    override val viewModel: FilmstripViewerViewModel by viewModels()
    private val uiViewModel: UiViewModel by activityViewModels()

    private lateinit var playback: FilmstripPlayback
    private lateinit var frameAdapter: FilmstripFrameAdapter
    private lateinit var thumbnailAdapter: FilmstripThumbnailAdapter
    private lateinit var detailsBehavior: BottomSheetBehavior<NestedScrollView>

    private val downloadRequester = MediaDownloadRequester(this) { binding.root }

    private var currentFrame: FilmstripFrame? = null

    // Frame a slide-out animation will move to once the media is off screen. A swipe that
    // interrupts the animation cancels its end action, so the move is made here instead. The
    // frame is kept by id: posts resolve into gallery frames while the animation runs, and an
    // index taken before that would land on a frame of the post just left.
    private var pendingSlide: PendingSlide? = null

    private data class PendingSlide(val targetFrameId: String, val direction: PostSwipeDirection)

    private var mediaGoneDialog: AlertDialog? = null

    // False until the pager has been moved to the tapped post; a new view always starts at page 0.
    private var isPagerPlaced = false

    private var isStopped = false
    private var isCoveredByOtherFragment = false

    private var navigationVisibilityBeforeOpen = true
    private var detailsBasePaddingBottom = 0

    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        // The viewer stays resumed when the post page is added on top, so the back stack is the
        // only signal that something now covers it.
        val isCovered = isCoveredByOtherFragmentNow()
        if (isCovered != isCoveredByOtherFragment) {
            isCoveredByOtherFragment = isCovered
            applyPlaybackSuspension()
            applyRequestedOrientation()
            if (!isCovered) uiViewModel.setNavigationVisibility(false)
        }
    }

    private fun isCoveredByOtherFragmentNow(): Boolean {
        val topFragment = parentFragmentManager.fragments.lastOrNull()
        return topFragment != null && topFragment !== this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.open(sourceFromArguments())
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
        if (!viewModel.hasMedia) {
            parentFragmentManager.popBackStack()
            return
        }

        isPagerPlaced = false
        // After a rotation under a post page the viewer comes back already covered.
        isCoveredByOtherFragment = isCoveredByOtherFragmentNow()
        navigationVisibilityBeforeOpen = uiViewModel.navigationVisibility.value
        uiViewModel.setNavigationVisibility(false)

        initPlayback()
        initPager()
        initThumbnails()
        showControlsForViewerKind()
        initOverlay()
        initDetailsSheet()
        bindViewModel()
        applyRequestedOrientation()

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)
    }

    override fun applyInsets(view: View) {
        detailsBasePaddingBottom = binding.detailsContent.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            // The bars are hidden together with the overlay; reading their visible size would
            // shift the overlay while it fades.
            val bars = windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
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
        mediaGoneDialog?.dismiss()
        mediaGoneDialog = null
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
        if (::playback.isInitialized) playback.release()
        showSystemBars(true)
        // The screen rotates back only when the viewer really closes; a rotation also destroys
        // the view, and letting go of the orientation then would rotate the screen twice.
        if (activity?.isChangingConfigurations != true) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        uiViewModel.setNavigationVisibility(navigationVisibilityBeforeOpen)
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Rotation recreates the viewer; only a real close ends the feed session.
        if (activity?.isChangingConfigurations == true) return

        val ownsSession = arguments?.getBoolean(KEY_OWNS_SESSION, true) ?: true
        if (ownsSession) viewModel.endSession() else viewModel.closeBorrowedSession()
    }

    // The page opened from this viewer continues on the posts this viewer is showing.
    override fun feedSessionForPostPage(): Int? {
        return (sourceFromArguments() as? FilmstripViewerSource.PostSession)?.sessionId
    }

    private fun close() {
        val postPage = postPageLeftBehind()
        parentFragmentManager.popBackStack()
        // The page shows a post the user has swiped away from, so going back skips it and lands
        // on the feed, which reveals the post last viewed.
        postPage?.close()
    }

    /**
     * The post page underneath, when this viewer was opened from it and has since been swiped to
     * another post; null while the page still matches the post on screen.
     */
    private fun postPageLeftBehind(): PostDetailsFragment? {
        val arguments = arguments ?: return null
        if (!arguments.getBoolean(KEY_RETURNS_TO_POST_PAGE)) return null

        val currentPostId = currentFrame?.source?.key ?: return null
        if (currentPostId == arguments.getString(KEY_POST_ID)) return null

        val fragments = parentFragmentManager.fragments
        return fragments.getOrNull(fragments.indexOf(this) - 1) as? PostDetailsFragment
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
            onRetryResolution = { frame -> viewModel.retryResolution(frame.source.key) }
        )

        binding.postSwipeLayout.apply {
            canSwipeToPost = ::canSwipeToPost
            canPageSideways = ::canPageSideways
            onPostSwipe = ::swipeToPost
            onSwipeDrag = ::followPostSwipe
            onSwipeAbandoned = ::settlePagerInPlace
        }

        frameAdapter.areControlsVisible = viewModel.isOverlayVisible

        binding.viewPager.apply {
            adapter = frameAdapter
            // The view model remembers the page; the pager's own saved state would restore it
            // before the frames exist and then fight the placement below.
            isSaveEnabled = false
            getRecyclerView()?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            getRecyclerView()?.isNestedScrollingEnabled = false
            // A page is the whole screen, so a change animation (a frame that resolves, or
            // gains gallery frames) would only crossfade the screen with itself. The animator's
            // post-layout pass also walked a child without a holder and crashed the viewer.
            getRecyclerView()?.itemAnimator = null
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    frameAdapter.currentList.getOrNull(position)?.let(::selectFrame)
                }
            })
        }
    }

    private fun initThumbnails() {
        thumbnailAdapter = FilmstripThumbnailAdapter(::openThumbnail)
        binding.thumbnails.adapter = thumbnailAdapter
    }

    private fun openThumbnail(thumbnail: FilmstripFrame) {
        val targetIndex = frameAdapter.currentList.indexOfFirst { it.id == thumbnail.id }
        if (targetIndex < 0) return
        // Neighbours slide over; a far jump would scroll every page in between.
        val isNeighbour = abs(targetIndex - binding.viewPager.currentItem) <= 1
        binding.viewPager.setCurrentItem(targetIndex, isNeighbour)
    }

    /** Thumbnails of every frame of [frame]'s gallery, with [frame]'s own brought into view. */
    private fun showGalleryThumbnails(frame: FilmstripFrame) {
        val galleryFrames = frameAdapter.currentList.filter { it.source.key == frame.source.key }
        thumbnailAdapter.submitList(galleryFrames) {
            if (_binding == null) return@submitList
            thumbnailAdapter.setActiveFrameId(frame.id)
            binding.thumbnails.post { revealThumbnail(frame.indexInSource) }
        }
    }

    private fun revealThumbnail(index: Int) {
        val list = _binding?.thumbnails ?: return
        val layoutManager = list.layoutManager as? LinearLayoutManager ?: return
        val thumbnailSize = resources.getDimensionPixelSize(R.dimen.filmstrip_thumbnail_size)
        val centredLeft = (list.width - thumbnailSize) / 2
        val child = layoutManager.findViewByPosition(index)
        if (child != null) {
            list.smoothScrollBy(child.left - centredLeft, 0)
        } else {
            layoutManager.scrollToPositionWithOffset(index, centredLeft)
        }
    }

    private fun canSwipeToPost(): Boolean {
        val isDetailsOpen = detailsBehavior.state != BottomSheetBehavior.STATE_HIDDEN
        // A zoomed image keeps its vertical drags for panning.
        val isZoomedIn = frameAdapter.isFrameZoomedIn(
            binding.viewPager.currentItem,
            binding.viewPager.getRecyclerView()
        )
        return !isDetailsOpen && !isZoomedIn
    }

    /**
     * Horizontal paging stays inside the current gallery. Posts are changed by the vertical
     * swipe only; a quick thumb swipe that arcs sideways would otherwise page the viewer to the
     * neighbouring post, which looks like the pager going backwards.
     */
    private fun canPageSideways(towardsNext: Boolean): Boolean {
        val frame = currentFrame ?: return false
        return if (towardsNext) {
            frame.indexInSource < frame.framesInSource - 1
        } else {
            frame.indexInSource > 0
        }
    }

    private fun swipeToPost(direction: PostSwipeDirection) {
        val targetIndex = postSwipeTargetIndex(
            frameAdapter.currentList,
            binding.viewPager.currentItem,
            direction
        )

        if (targetIndex != null) {
            slideToPost(targetIndex, direction)
        } else {
            if (direction == PostSwipeDirection.NEXT) viewModel.requestMorePosts()
            settlePagerInPlace()
        }
    }

    /** Moves the media with the finger so a vertical swipe feels like the horizontal pager. */
    private fun followPostSwipe(verticalOffset: Float) {
        val direction =
            if (verticalOffset < 0F) PostSwipeDirection.NEXT else PostSwipeDirection.PREVIOUS
        val hasTarget = postSwipeTargetIndex(
            frameAdapter.currentList,
            binding.viewPager.currentItem,
            direction
        ) != null

        binding.viewPager.run {
            cancelPostSlide()
            // With no post to go to, the media resists the drag instead of following it fully.
            translationY = if (hasTarget) verticalOffset else verticalOffset * EDGE_DRAG_RESISTANCE
        }
    }

    private fun settlePagerInPlace() {
        val pager = _binding?.viewPager ?: return
        pager.cancelPostSlide()
        if (!areAnimationsEnabled()) {
            pager.translationY = 0F
            return
        }
        pager.animate()
            .translationY(0F)
            .setDuration(POST_SLIDE_IN_MILLIS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * The pager is horizontal, so the vertical move is done by hand: the current media slides
     * off in the swipe direction, the page changes while it is off screen, and the new media
     * slides in from the opposite edge.
     */
    private fun slideToPost(targetIndex: Int, direction: PostSwipeDirection) {
        val pager = binding.viewPager
        pager.cancelPostSlide()
        if (!areAnimationsEnabled()) {
            pager.setCurrentItem(targetIndex, false)
            pager.translationY = 0F
            return
        }

        val exitOffset = pager.height.toFloat() * if (direction == PostSwipeDirection.NEXT) -1F else 1F
        val targetFrame = frameAdapter.currentList.getOrNull(targetIndex) ?: return
        pendingSlide = PendingSlide(targetFrame.id, direction)
        pager.animate()
            .translationY(exitOffset)
            .setDuration(POST_SLIDE_OUT_MILLIS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                val currentPager = _binding?.viewPager ?: return@withEndAction
                currentPager.commitPendingSlide()
                currentPager.translationY = -exitOffset
                currentPager.animate()
                    .translationY(0F)
                    .setDuration(POST_SLIDE_IN_MILLIS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    /**
     * Stops the slide animation. A cancelled ViewPropertyAnimator never runs its end action, so
     * a page change the slide-out had not made yet is made here; otherwise a quick second swipe
     * would slide the post the user just left back in, as if the pager had gone backwards.
     */
    private fun ViewPager2.cancelPostSlide() {
        animate().cancel()
        commitPendingSlide()
    }

    /**
     * Moves the pager to the frame a slide was heading for. The frame is looked up again by id,
     * since the list may have changed during the slide; a frame that is gone (its post was
     * dropped) is replaced by whatever is now next to the current post in the same direction.
     */
    private fun ViewPager2.commitPendingSlide() {
        val slide = pendingSlide ?: return
        pendingSlide = null

        val frames = frameAdapter.currentList
        val targetIndex = frames.indexOfFirst { it.id == slide.targetFrameId }
            .takeIf { it >= 0 }
            ?: postSwipeTargetIndex(frames, currentItem, slide.direction)
            ?: return
        setCurrentItem(targetIndex, false)
    }

    private fun areAnimationsEnabled(): Boolean {
        return Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1F
        ) > 0F
    }

    private fun initOverlay() {
        binding.run {
            overlay.isVisible = viewModel.isOverlayVisible
            showSystemBars(viewModel.isOverlayVisible)

            buttonClose.setOnClickListener { close() }
            railComments.setOnClickListener { openPost() }
            buttonSave.setOnClickListener { toggleSave() }
            buttonRotate.setOnClickListener { toggleOrientation() }
            buttonShareLink.setOnClickListener { shareLink() }
            buttonDownload.setOnClickListener { downloadCurrentMedia() }
            buttonMute.setOnCheckedChangeListener { _, isMuted -> viewModel.setMuted(isMuted) }
        }
        initInfoGestures()
    }

    /** A link has no post, so the post's score, comments and save give way to share and download. */
    private fun showControlsForViewerKind() {
        val isLinkViewer = viewModel.isLinkViewer
        binding.run {
            rowPostSource.isVisible = !isLinkViewer
            textLinkCaption.isVisible = isLinkViewer
            railScore.isVisible = !isLinkViewer
            railComments.isVisible = !isLinkViewer
            buttonSave.isVisible = !isLinkViewer
            buttonShareLink.isVisible = isLinkViewer
            buttonDownload.isVisible = isLinkViewer
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initInfoGestures() {
        if (viewModel.isLinkViewer) {
            // Nothing to expand: a tap on the caption falls through to the page and toggles the overlay.
            binding.info.run {
                isClickable = false
                isFocusable = false
                contentDescription = null
            }
            return
        }

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

        // The names are their own targets inside the info block, so a tap on them goes straight
        // to that page instead of opening the details first.
        val openCurrentSubreddit = View.OnClickListener {
            currentFrame?.post?.let { post -> openSubreddit(post.subredditName) }
        }
        binding.avatarCommunity.setOnClickListener(openCurrentSubreddit)
        binding.textSubreddit.setOnClickListener(openCurrentSubreddit)
        binding.textAuthorAge.setOnClickListener {
            currentFrame?.post?.let { post -> openUser(post.author) }
        }

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
                // Opening the post page destroys this view while the sheet is still settling, and
                // the settle animation keeps calling back afterwards, so the binding may be gone.
                override fun onStateChanged(sheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            viewModel.isDetailsExpanded = true
                            // Restoring an open sheet after rotation skips onSlide.
                            _binding?.detailsDim?.isVisible = true
                            _binding?.detailsDim?.alpha = 1F
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            viewModel.isDetailsExpanded = false
                            _binding?.detailsDim?.isVisible = false
                        }
                    }
                }

                override fun onSlide(sheet: View, slideOffset: Float) {
                    val detailsDim = _binding?.detailsDim ?: return
                    detailsDim.isVisible = true
                    detailsDim.alpha = slideOffset.coerceIn(0F, 1F)
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
                viewModel.isLinkMediaGone.collect { isGone -> if (isGone) showMediaGoneDialog() }
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
        bindOverlay(frame)
    }

    private fun bindOverlay(frame: FilmstripFrame) {
        currentFrame = frame

        binding.run {
            thumbnails.isVisible = frame.isGalleryFrame
            textPosition.isVisible = frame.isGalleryFrame
            if (frame.isGalleryFrame) showGalleryThumbnails(frame)
            textPosition.text = getString(
                R.string.filmstrip_position,
                frame.indexInSource + 1,
                frame.framesInSource
            )

            // The mute button reappears when the player reports an audio track.
            if (!frame.isVideo) buttonMute.isVisible = false

            buttonDownload.isEnabled = frame.status == FrameStatus.READY
            buttonDownload.alpha = if (buttonDownload.isEnabled) 1F else DISABLED_ACTION_ALPHA
        }

        val post = frame.post
        if (post != null) {
            bindPostOverlay(frame, post)
        } else {
            bindLinkOverlay(frame.source as FrameSource.OfLink)
        }
    }

    private fun bindLinkOverlay(link: FrameSource.OfLink) {
        binding.textTitle.text = Uri.parse(link.url).host?.removePrefix("www.") ?: link.url
    }

    private fun bindPostOverlay(frame: FilmstripFrame, post: PostEntity) {
        val age = DateUtil.getTimeDifference(requireContext(), post.created, false)

        binding.run {
            avatarCommunity.setText(post.subredditName)
            textSubreddit.text = post.subreddit
            textAuthorAge.text = getString(R.string.filmstrip_info_meta, post.author, age)
            textSubreddit.contentDescription =
                getString(R.string.filmstrip_go_to_subreddit, post.subredditName)
            textAuthorAge.contentDescription =
                getString(R.string.filmstrip_go_to_user, post.author)
            textTitle.text = post.title

            textScore.text = post.score
            railScore.contentDescription = getString(R.string.filmstrip_score_description, post.score)
            textComments.text = post.commentsNumber
            railComments.contentDescription = getString(
                R.string.filmstrip_comments_description,
                post.commentsNumber
            )
        }

        bindSavedState(post)
        bindDetails(frame, post, age)
    }

    private fun bindDetails(frame: FilmstripFrame, post: PostEntity, age: String) {
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

    /** Turns the screen the other way round and keeps it there while the viewer is on top. */
    private fun toggleOrientation() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        viewModel.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        applyRequestedOrientation()
    }

    /**
     * The orientation the rotate button chose holds only while the viewer is the top screen. A
     * post page opened on top gets the device's own orientation, and the viewer takes its
     * chosen one back when the page closes.
     */
    private fun applyRequestedOrientation() {
        val activity = activity ?: return
        activity.requestedOrientation = if (isCoveredByOtherFragment) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            viewModel.requestedOrientation
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

    private fun shareLink() {
        val link = currentFrame?.source as? FrameSource.OfLink ?: return
        shareExternalLink(link.url)
    }

    private fun downloadCurrentMedia() {
        currentFrame?.media?.let { downloadRequester.request(it) }
    }

    private fun openPost() {
        val post = currentFrame?.post ?: return
        collapseDetails()
        if (arguments?.getBoolean(KEY_RETURNS_TO_POST_PAGE) != true) {
            onClick(post)
            return
        }

        val fragmentManager = parentFragmentManager
        if (postPageLeftBehind() == null) {
            // The post page this viewer was opened from is right underneath; a second copy of it
            // on top would need two back presses to get out of.
            fragmentManager.popBackStack()
        } else {
            // The page underneath is of the post this viewer was opened on. The page of the post
            // on screen takes its place, so back from it still leads to the feed.
            fragmentManager.popBackStack()
            fragmentManager.popBackStack()
            onClick(fragmentManager, post)
        }
    }

    private fun showMediaGoneDialog() {
        if (mediaGoneDialog?.isShowing == true) return
        mediaGoneDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_media_not_found_title)
            .setMessage(R.string.dialog_media_not_found_body)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> close() }
            .setCancelable(false)
            .show()
    }

    private fun sourceFromArguments(): FilmstripViewerSource {
        val arguments = requireArguments()
        val link = arguments.getString(KEY_LINK)
        if (link != null) {
            val mediaType = arguments.serializable(KEY_MEDIA_TYPE) ?: MediaType.LINK
            return FilmstripViewerSource.MediaLink(link, mediaType)
        }
        return FilmstripViewerSource.PostSession(
            arguments.getInt(KEY_SESSION_ID),
            arguments.getString(KEY_POST_ID).orEmpty()
        )
    }

    private fun toggleOverlay() {
        setOverlayVisible(!viewModel.isOverlayVisible)
    }

    private fun setOverlayVisible(isVisible: Boolean) {
        viewModel.isOverlayVisible = isVisible
        binding.overlay.showWithAlpha(isVisible, OVERLAY_FADE_MILLIS)
        frameAdapter.areControlsVisible = isVisible
        showSystemBars(isVisible)
    }

    private fun showSystemBars(show: Boolean) {
        val window = activity?.window ?: return
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (show) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
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
        private const val KEY_SESSION_ID = "KEY_SESSION_ID"
        private const val KEY_RETURNS_TO_POST_PAGE = "KEY_RETURNS_TO_POST_PAGE"
        private const val KEY_OWNS_SESSION = "KEY_OWNS_SESSION"
        private const val KEY_LINK = "KEY_LINK"
        private const val KEY_MEDIA_TYPE = "KEY_MEDIA_TYPE"
        private const val SINGLE_COMMENT = "1"
        private const val OVERLAY_FADE_MILLIS = 200L
        private const val POST_SLIDE_OUT_MILLIS = 140L
        private const val POST_SLIDE_IN_MILLIS = 220L

        // Fraction of the finger's travel the media moves when there is no post in that direction.
        private const val EDGE_DRAG_RESISTANCE = 0.3F
        private const val DISABLED_ACTION_ALPHA = 0.4F

        /**
         * Viewer on the posts published under [sessionId], opened on [postId]. With
         * [returnsToPostPage] the comments buttons close the viewer, because the page of that
         * post is the screen underneath. A viewer that does not own the session ([ownsSession]
         * false) borrowed it from that page and leaves it open when it closes.
         */
        fun newInstance(
            sessionId: Int,
            postId: String,
            returnsToPostPage: Boolean,
            ownsSession: Boolean = true
        ) = FilmstripViewerFragment().apply {
            arguments = bundleOf(
                KEY_SESSION_ID to sessionId,
                KEY_POST_ID to postId,
                KEY_RETURNS_TO_POST_PAGE to returnsToPostPage,
                KEY_OWNS_SESSION to ownsSession
            )
        }

        /** Viewer on a media link that has no post behind it. */
        fun newInstance(link: String, mediaType: MediaType) = FilmstripViewerFragment().apply {
            arguments = bundleOf(KEY_LINK to link, KEY_MEDIA_TYPE to mediaType)
        }
    }
}
