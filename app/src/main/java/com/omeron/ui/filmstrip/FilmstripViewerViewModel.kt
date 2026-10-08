package com.omeron.ui.filmstrip

import androidx.lifecycle.viewModelScope
import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.MediaType
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/** What a viewer was opened on. The fragment's arguments hold it, so a restart can rebuild it. */
sealed interface FilmstripViewerSource {

    /** The posts a list (or a single post) published under [sessionId]. */
    data class PostSession(val sessionId: Int, val initialPostId: String) : FilmstripViewerSource

    /** A media link with no post behind it. */
    data class MediaLink(val url: String, val mediaType: MediaType) : FilmstripViewerSource
}

@HiltViewModel
class FilmstripViewerViewModel @Inject constructor(
    private val feedHolder: FilmstripFeedHolder,
    private val mediaResolver: MediaResolver,
    private val preferencesRepository: PreferencesRepository,
    postListRepository: PostListRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, postListRepository) {

    private val source = MutableStateFlow<FilmstripViewerSource?>(null)

    private val mediaStates = MutableStateFlow<Map<String, PostMediaState>>(emptyMap())

    private val linkMediaState = MutableStateFlow<PostMediaState>(PostMediaState.Loading)

    private var linkResolutionJob: Job? = null

    // Full posts fetched for feed posts that are only pointers (search results), by feed post id.
    private val hydratedPosts = MutableStateFlow<Map<String, PostEntity>>(emptyMap())

    private val resolutionJobs = mutableMapOf<String, Job>()

    private val viewedPostIds = mutableSetOf<String>()

    // Posts the user has paged to. A pointer post is only recorded in the history once its full
    // data is here, because a history entry without a link could not be opened again.
    private val selectedPostIds = mutableSetOf<String>()

    val frames: StateFlow<List<FilmstripFrame>> = source
        .flatMapLatest { openedSource ->
            when (openedSource) {
                null -> flowOf(emptyList())
                is FilmstripViewerSource.PostSession -> postFrames(openedSource.sessionId)
                is FilmstripViewerSource.MediaLink -> linkFrames(openedSource)
            }
        }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _isLinkMediaGone = MutableStateFlow(false)

    /** True when the server said the linked media does not exist (any more) or is forbidden. */
    val isLinkMediaGone: StateFlow<Boolean> = _isLinkMediaGone

    /** The viewer is made for a link, not for posts, so there is no post to show or save. */
    val isLinkViewer: Boolean
        get() = source.value is FilmstripViewerSource.MediaLink

    /** Survives rotation, so the pager returns to the same frame. */
    var currentFrameId: String? = null
        private set

    var isOverlayVisible = true

    var isDetailsExpanded = false

    /** False after a process restart, when the posts this viewer was paging are gone. */
    val hasMedia: Boolean
        get() = when (val openedSource = source.value) {
            null -> false
            is FilmstripViewerSource.PostSession -> feedHolder.isOpen(openedSource.sessionId)
            is FilmstripViewerSource.MediaLink -> true
        }

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

    /** Sets what to show; a viewer that already has a source (after rotation) keeps it. */
    fun open(openedSource: FilmstripViewerSource) {
        if (source.value != null) return
        source.value = openedSource
        if (openedSource is FilmstripViewerSource.MediaLink) resolveLink(openedSource)
    }

    /** Index of the frame the pager should open on. */
    fun initialFrameIndex(frames: List<FilmstripFrame>): Int {
        val restoredIndex = frames.indexOfFirst { it.id == currentFrameId }
        if (restoredIndex >= 0) return restoredIndex

        val initialPostId = (source.value as? FilmstripViewerSource.PostSession)?.initialPostId
        return frames.indexOfFirst { it.source.key == initialPostId }.coerceAtLeast(0)
    }

    fun onFrameSelected(frame: FilmstripFrame) {
        currentFrameId = frame.id
        val post = frame.post ?: return
        selectedPostIds.add(post.id)
        // The feed's own entity is what dims the grid tile, so it is marked even before hydration.
        post.seen = true
        if (!post.needsHydration) markViewed(post)
        onFramesChanged()
    }

    /** Looks up the media of the frames' source again, after a failed lookup. */
    fun retryResolution(sourceKey: String) {
        val openedSource = source.value
        if (openedSource is FilmstripViewerSource.MediaLink) {
            resolveLink(openedSource)
            return
        }

        val post = currentPosts().firstOrNull { it.id == sourceKey } ?: return
        startResolution(post)
    }

    /** Asks the feed for another page, for a swipe that ran into the last loaded post. */
    fun requestMorePosts() {
        sessionId()?.let(feedHolder::requestMore)
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        viewModelScope.launch { preferencesRepository.setMuteVideo(muted) }
    }

    /** Tells the list behind this viewer that it closed. A link viewer has no list. */
    fun endSession() {
        val sessionId = sessionId() ?: return
        val lastPostId = frames.value.firstOrNull { it.id == currentFrameId }?.post?.id
        feedHolder.endSession(sessionId, lastPostId)
    }

    private fun sessionId(): Int? {
        return (source.value as? FilmstripViewerSource.PostSession)?.sessionId
    }

    private fun postFrames(sessionId: Int): Flow<List<FilmstripFrame>> {
        return combine(
            feedHolder.postsOf(sessionId),
            hydratedPosts,
            mediaStates
        ) { posts, hydrated, states ->
            buildFilmstripFrames(withHydratedPosts(posts, hydrated), states, ::instantMediaFor)
        }
    }

    private fun linkFrames(link: FilmstripViewerSource.MediaLink): Flow<List<FilmstripFrame>> {
        return linkMediaState.map { mediaState ->
            val instantMedia = mediaResolver.resolveWithoutNetwork(link.url, link.mediaType)
            buildLinkFrames(link.url, mediaState, instantMedia)
        }
    }

    private fun resolveLink(link: FilmstripViewerSource.MediaLink) {
        // Nothing to look up: the frames are built straight from the link.
        if (mediaResolver.resolveWithoutNetwork(link.url, link.mediaType) != null) return

        linkResolutionJob?.cancel()
        linkMediaState.value = PostMediaState.Loading
        linkResolutionJob = viewModelScope.launch {
            linkMediaState.value = try {
                PostMediaState.Resolved(mediaResolver.resolve(link.url, link.mediaType))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (httpException: HttpException) {
                if (httpException.code() in MEDIA_GONE_HTTP_CODES) _isLinkMediaGone.value = true
                PostMediaState.Failed
            } catch (throwable: Throwable) {
                PostMediaState.Failed
            }
        }
    }

    private fun markViewed(post: PostEntity) {
        if (viewedPostIds.add(post.id)) {
            post.seen = true
            insertPostInHistory(post)
        }
    }

    private fun currentPosts(): List<PostEntity> {
        val sessionId = sessionId() ?: return emptyList()
        return withHydratedPosts(feedHolder.currentPostsOf(sessionId), hydratedPosts.value)
    }

    private fun onFramesChanged() {
        val sessionId = sessionId() ?: return
        val posts = currentPosts()
        val currentIndex = posts.indexOfFirst { it.id == currentPostId() }
        if (currentIndex < 0) return

        resolveIfNeeded(posts[currentIndex])
        // One post of lookahead so the next swipe lands on a frame that is already resolved.
        posts.getOrNull(currentIndex + 1)?.let(::resolveIfNeeded)

        if (posts.size - currentIndex - 1 <= LOAD_MORE_DISTANCE_IN_POSTS) {
            feedHolder.requestMore(sessionId)
        }
    }

    private fun currentPostId(): String? = currentFrameId?.substringBeforeLast('#')

    private fun instantMediaFor(post: PostEntity): List<GalleryMedia>? {
        // A pointer post has no link yet; building media from its empty link would yield a
        // broken frame instead of waiting for the full post.
        if (post.needsHydration) return null
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
                val fullPost = if (post.needsHydration) hydrate(post) else post
                PostMediaState.Resolved(resolveMedia(fullPost, fallbackPreview = post.preview))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                PostMediaState.Failed
            }
            setMediaState(post.id, outcome)
        }
    }

    private suspend fun resolveMedia(post: PostEntity, fallbackPreview: String?): List<GalleryMedia> {
        // A hydrated gallery post already carries its images; resolving it again would fetch the
        // same page a second time.
        if (post.gallery.isNotEmpty()) return post.gallery

        return try {
            mediaResolver.resolve(post.mediaUrl, post.mediaType)
        } catch (unsupported: MediaResolver.UnsupportedMediaException) {
            // The feed listed it as media, so the preview is the best thing left to show.
            previewFallback(post.preview ?: fallbackPreview)
        }
    }

    // The frame keeps its place in the pager: the full post replaces the pointer under the same
    // id, so the overlay and details sheet pick up the body text and domain.
    private suspend fun hydrate(pointerPost: PostEntity): PostEntity {
        val fullPost = mediaResolver.fetchFullPost(pointerPost.permalink).apply {
            seen = pointerPost.seen
            saved = pointerPost.saved
        }
        hydratedPosts.value = hydratedPosts.value + (pointerPost.id to fullPost)
        if (pointerPost.id in selectedPostIds) {
            pointerPost.seen = true
            markViewed(fullPost)
        }
        return fullPost
    }

    private fun previewFallback(preview: String?): List<GalleryMedia> {
        if (preview == null) return emptyList()
        return GalleryMedia.singleton(GalleryMedia.Type.IMAGE, preview)
    }

    private fun setMediaState(postId: String, state: PostMediaState) {
        mediaStates.value = mediaStates.value + (postId to state)
    }

    companion object {
        // Ask the feed for another page when this few posts remain after the current one.
        private const val LOAD_MORE_DISTANCE_IN_POSTS = 3

        private val MEDIA_GONE_HTTP_CODES = listOf(403, 404)
    }
}
