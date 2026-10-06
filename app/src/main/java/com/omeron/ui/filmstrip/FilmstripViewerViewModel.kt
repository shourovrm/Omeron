package com.omeron.ui.filmstrip

import androidx.lifecycle.viewModelScope
import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.db.PostEntity
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.ui.base.BaseViewModel
import com.omeron.ui.mediaviewer.MediaResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilmstripViewerViewModel @Inject constructor(
    private val feedHolder: FilmstripFeedHolder,
    private val mediaResolver: MediaResolver,
    private val preferencesRepository: PreferencesRepository,
    postListRepository: PostListRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, postListRepository) {

    private val mediaStates = MutableStateFlow<Map<String, PostMediaState>>(emptyMap())

    private val resolutionJobs = mutableMapOf<String, Job>()

    private val viewedPostIds = mutableSetOf<String>()

    val frames: StateFlow<List<FilmstripFrame>> = combine(feedHolder.posts, mediaStates) { posts, states ->
        buildFilmstripFrames(posts, states, ::instantMediaFor)
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    /** Post the user tapped in the grid; only used to pick the first page. */
    var initialPostId: String? = null

    /** Survives rotation, so the pager returns to the same frame. */
    var currentFrameId: String? = null
        private set

    var isOverlayVisible = true

    var isDetailsExpanded = false

    val hasFeed: Boolean
        get() = feedHolder.activeSessionId != null

    init {
        viewModelScope.launch {
            preferencesRepository.getMuteVideo(false).collect { _isMuted.value = it }
        }

        // The posts to resolve depend on the current page, and new posts or finished lookups
        // change what "the next post" is, so every list change re-checks.
        viewModelScope.launch {
            frames.collect { onFramesChanged() }
        }
    }

    /** Index of the frame the pager should open on. */
    fun initialFrameIndex(frames: List<FilmstripFrame>): Int {
        val restoredIndex = frames.indexOfFirst { it.id == currentFrameId }
        if (restoredIndex >= 0) return restoredIndex
        return frames.indexOfFirst { it.post.id == initialPostId }.coerceAtLeast(0)
    }

    fun onFrameSelected(frame: FilmstripFrame) {
        currentFrameId = frame.id
        if (viewedPostIds.add(frame.post.id)) {
            frame.post.seen = true
            insertPostInHistory(frame.post)
        }
        onFramesChanged()
    }

    fun retryResolution(postId: String) {
        val post = feedHolder.posts.value.firstOrNull { it.id == postId } ?: return
        startResolution(post)
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        viewModelScope.launch { preferencesRepository.setMuteVideo(muted) }
    }

    fun endSession() {
        val lastPostId = frames.value.firstOrNull { it.id == currentFrameId }?.post?.id
        feedHolder.endSession(lastPostId)
    }

    private fun onFramesChanged() {
        val posts = feedHolder.posts.value
        val currentIndex = posts.indexOfFirst { it.id == currentPostId() }
        if (currentIndex < 0) return

        resolveIfNeeded(posts[currentIndex])
        // One post of lookahead so the next swipe lands on a frame that is already resolved.
        posts.getOrNull(currentIndex + 1)?.let(::resolveIfNeeded)

        if (posts.size - currentIndex - 1 <= LOAD_MORE_DISTANCE_IN_POSTS) {
            feedHolder.requestMore()
        }
    }

    private fun currentPostId(): String? = currentFrameId?.substringBeforeLast('#')

    private fun instantMediaFor(post: PostEntity): List<GalleryMedia>? {
        return mediaResolver.resolveWithoutNetwork(post.mediaUrl, post.mediaType)
    }

    private fun resolveIfNeeded(post: PostEntity) {
        val needsLookup = post.gallery.isEmpty() && instantMediaFor(post) == null
        if (needsLookup && !mediaStates.value.containsKey(post.id)) {
            startResolution(post)
        }
    }

    private fun startResolution(post: PostEntity) {
        resolutionJobs.remove(post.id)?.cancel()
        setMediaState(post.id, PostMediaState.Loading)

        resolutionJobs[post.id] = viewModelScope.launch {
            val outcome = try {
                PostMediaState.Resolved(mediaResolver.resolve(post.mediaUrl, post.mediaType))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unsupported: MediaResolver.UnsupportedMediaException) {
                // The feed listed it as media, so the preview is the best thing left to show.
                PostMediaState.Resolved(previewFallback(post))
            } catch (throwable: Throwable) {
                PostMediaState.Failed
            }
            setMediaState(post.id, outcome)
        }
    }

    private fun previewFallback(post: PostEntity): List<GalleryMedia> {
        val preview = post.preview ?: return emptyList()
        return GalleryMedia.singleton(GalleryMedia.Type.IMAGE, preview)
    }

    private fun setMediaState(postId: String, state: PostMediaState) {
        mediaStates.value = mediaStates.value + (postId to state)
    }

    companion object {
        // Ask the feed for another page when this few posts remain after the current one.
        private const val LOAD_MORE_DISTANCE_IN_POSTS = 3
    }
}
