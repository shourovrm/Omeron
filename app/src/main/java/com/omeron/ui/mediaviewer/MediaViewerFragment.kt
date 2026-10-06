package com.omeron.ui.mediaviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.omeron.R
import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.MediaType
import com.omeron.data.model.Resource
import com.omeron.databinding.FragmentMediaViewerBinding
import com.omeron.ui.common.FullscreenBottomSheetFragment
import com.omeron.util.extension.betterSmoothScrollToPosition
import com.omeron.util.extension.clearWindowInsetsListener
import com.omeron.util.extension.getRecyclerView
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.parcelableArrayList
import com.omeron.util.extension.serializable
import com.omeron.util.extension.showWithAlpha
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MediaViewerFragment : FullscreenBottomSheetFragment() {

    private var _binding: FragmentMediaViewerBinding? = null
    private val binding get() = _binding!!

    private val viewerViewModel: MediaViewerViewModel by viewModels()

    private val args: MediaViewerFragmentArgs by navArgs()

    // Flag to check if fragment was open from FragmentManager or Navigation
    private var isLegacyNavigation: Boolean = false

    private lateinit var mediaAdapter: MediaViewerAdapter
    private lateinit var thumbnailAdapter: MediaViewerThumbnailAdapter

    private val downloadRequester = MediaDownloadRequester(this) { binding.root }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleArguments()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        behavior?.skipCollapsed = true

        showSystemBars(false)

        applyInsets()

        initRecyclerView()
        initViewPager()
        bindViewModel()
        binding.run {
            buttonDownload.setOnClickListener { requestMediaDownload() }
            infoRetry.setActionClickListener { retry() }
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { rootView, windowInsets ->
            windowInsets.displayCutout?.let { cutout ->
                binding.run {
                    infoRetry.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin += cutout.safeInsetTop
                    }

                    controls.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin += cutout.safeInsetTop
                    }

                    pageCounter.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin += cutout.safeInsetTop
                    }
                }
            }

            rootView.clearWindowInsetsListener()

            windowInsets
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewerViewModel.media.collect {
                    binding.loadingCradle.isVisible = it is Resource.Loading
                    when (it) {
                        is Resource.Success -> bindMedia(it.data)
                        is Resource.Error -> handleError(it.code)
                        is Resource.Loading -> {
                            // ignore
                        }
                    }
                }
            }

            launch {
                viewerViewModel.selectedPage.collect {
                    binding.listThumbnails.betterSmoothScrollToPosition(it)
                    thumbnailAdapter.selectItem(it)
                    binding.textPageCurrent.text = it.plus(1).toString()
                }
            }
        }
    }

    private fun initRecyclerView() {
        thumbnailAdapter = MediaViewerThumbnailAdapter(this::onThumbnailClick)
        binding.listThumbnails.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = thumbnailAdapter
        }
    }

    private fun initViewPager() {
        val muteVideo = runBlocking { viewerViewModel.isVideoMuted.first() }

        mediaAdapter = MediaViewerAdapter(
            requireContext(),
            muteVideo,
            onMediaClick = {
                viewerViewModel.hideControls = !viewerViewModel.hideControls
                showControls(!binding.controls.isVisible)
            },
            showControls = { show ->
                if (!show || !viewerViewModel.hideControls) {
                    showControls(show)
                }
            },
            hasAudio = {
                binding.buttonMute.isVisible = it
            }
        )

        binding.buttonMute.run {
            isChecked = muteVideo
            setOnCheckedChangeListener { _, isMuted -> muteAudio(isMuted) }
        }

        binding.viewPager.apply {
            adapter = mediaAdapter
            getRecyclerView()?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            getRecyclerView()?.isNestedScrollingEnabled = false
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    viewerViewModel.setSelectedPage(position)
                }
            })
        }
    }

    private fun onThumbnailClick(position: Int) {
        binding.viewPager.currentItem = position
    }

    private fun handleArguments() {
        if (args.images != null) {
            viewerViewModel.setMedia(args.images!!.toList())
        } else if (args.link != null && args.type != MediaType.NO_MEDIA) {
            viewerViewModel.loadMedia(args.link!!, args.type)
        } else {
            arguments?.let { bundle ->
                if (bundle.containsKey(BUNDLE_KEY_IMAGES)) {
                    val images = bundle.parcelableArrayList<GalleryMedia>(BUNDLE_KEY_IMAGES)
                    if (images != null) {
                        viewerViewModel.setMedia(images.toList())
                    }
                } else if (bundle.containsKey(BUNDLE_KEY_LINK)) {
                    val link = bundle.getString(BUNDLE_KEY_LINK, "")
                    val type = bundle.serializable(BUNDLE_KEY_TYPE) ?: MediaType.LINK
                    viewerViewModel.loadMedia(link, type)
                }
                isLegacyNavigation = true
            }
        }
    }

    private fun bindMedia(media: List<GalleryMedia>) {
        if (media.size > 1) {
            thumbnailAdapter.submitData(media)
            binding.textPageCount.text = media.size.toString()
        }
        mediaAdapter.submitData(media)
    }

    private fun muteAudio(shouldMute: Boolean) {
        val currentItemPosition = viewerViewModel.selectedPage.value
        val videoViewHolder = binding.viewPager
            .getRecyclerView()
            ?.findViewHolderForAdapterPosition(currentItemPosition)
                as? MediaViewerAdapter.VideoViewHolder

        videoViewHolder?.muteAudio(shouldMute)

        mediaAdapter.muteVideo = shouldMute

        viewerViewModel.setMuted(shouldMute)
    }

    private fun requestMediaDownload() {
        val page = viewerViewModel.selectedPage.value
        mediaAdapter.getItem(page)?.let { downloadRequester.request(it) }
    }

    private fun handleError(code: Int?) {
        when (code) {
            403, 404 -> showNotFoundDialog()
            else -> showRetryBar()
        }
    }

    private fun retry() {
        if (args.link != null) {
            viewerViewModel.loadMedia(args.link!!, args.type, true)
        } else {
            val link = arguments?.getString(BUNDLE_KEY_LINK)
            val type = arguments?.serializable<MediaType>(BUNDLE_KEY_TYPE)
            if (link != null && type != null) {
                viewerViewModel.loadMedia(link, type, true)
            }
        }
    }

    private fun showRetryBar() {
        if (!binding.infoRetry.isVisible) {
            binding.infoRetry.show()
        }
    }

    private fun showNotFoundDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_media_not_found_title)
            .setMessage(R.string.dialog_media_not_found_body)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showSystemBars(show: Boolean) {
        dialog?.window?.let { window ->
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            if (show) {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun showControls(show: Boolean) {
        val duration = 250L

        binding.run {
            if (controls.isVisible != show) {
                controls.showWithAlpha(show, duration)
            }

            if (viewerViewModel.isMultiMedia.value) {
                if (pageCounter.isVisible != show) {
                    pageCounter.showWithAlpha(show, duration)
                }

                if (listThumbnails.isVisible != show) {
                    listThumbnails.showWithAlpha(show, duration)
                }
            }
        }
    }

    override fun getTheme(): Int {
        return R.style.ThemeOverlay_App_BottomSheetDialog_MediaViewer
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showSystemBars(true)
        mediaAdapter.clear()
        _binding = null
    }

    companion object {
        const val TAG = "ImageViewerFragment"

        private const val BUNDLE_KEY_IMAGES = "BUNDLE_KEY_IMAGES"
        private const val BUNDLE_KEY_LINK = "BUNDLE_KEY_LINK"
        private const val BUNDLE_KEY_TYPE = "BUNDLE_KEY_TYPE"

        fun newInstance(images: List<GalleryMedia>) = MediaViewerFragment().apply {
            arguments = bundleOf(
                BUNDLE_KEY_IMAGES to images
            )
        }

        fun newInstance(link: String, type: MediaType) = MediaViewerFragment().apply {
            arguments = bundleOf(
                BUNDLE_KEY_LINK to link,
                BUNDLE_KEY_TYPE to type
            )
        }
    }
}
