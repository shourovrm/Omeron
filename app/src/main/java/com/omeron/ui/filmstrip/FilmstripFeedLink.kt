package com.omeron.ui.filmstrip

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omeron.data.model.db.PostEntity
import com.omeron.ui.postlist.PostListAdapter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The feed list's side of the Filmstrip hand-off: publishes the adapter's loaded media posts to
 * the [FilmstripFeedHolder], loads another page when the viewer asks, and scrolls the list to the
 * last viewed post when the viewer closes. Lives as long as the list's view.
 *
 * The adapter holds every post when the list is not in the Filmstrip layout, so the published
 * posts are the adapter's posts that pass [isFilmstripMedia]; their positions are not adapter
 * positions.
 */
class FilmstripFeedLink private constructor(
    private val holder: FilmstripFeedHolder,
    val sessionId: Int,
    private val adapter: PostListAdapter,
    private val list: RecyclerView,
    private val isFilmstripMedia: (PostEntity) -> Boolean,
    lifecycleOwner: LifecycleOwner
) {

    // Set while a viewer request for more media is unanswered. A page of only text posts leaves
    // the published list unchanged, which the viewer cannot tell apart from "nothing arrived yet",
    // so the link keeps loading on its behalf.
    private var mediaCountAtLoadRequest: Int? = null

    private var appendLoadState: LoadState = LoadState.NotLoading(endOfPaginationReached = false)

    private val trackAppendLoadState: (CombinedLoadStates) -> Unit = { loadStates ->
        appendLoadState = loadStates.append
    }

    private val publishLoadedPosts: () -> Unit = {
        val mediaPosts = loadedMediaPosts()
        holder.publish(sessionId, mediaPosts)
        continueLoadingUntilMediaArrives(mediaPosts.size)
    }

    init {
        adapter.addLoadStateListener(trackAppendLoadState)
        adapter.addOnPagesUpdatedListener(publishLoadedPosts)

        lifecycleOwner.lifecycleScope.launch {
            launch {
                holder.loadMoreRequests.collect { requestedSessionId ->
                    if (requestedSessionId == sessionId) loadNextPage()
                }
            }
            launch {
                holder.viewerClosed.collect { closed ->
                    if (closed.sessionId == sessionId) revealPost(closed.lastPostId)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = dispose()
        })
    }

    fun dispose() {
        adapter.removeLoadStateListener(trackAppendLoadState)
        adapter.removeOnPagesUpdatedListener(publishLoadedPosts)
    }

    // Paging loads the next page when an item near the end is read, so reading the last one is
    // how the viewer's request reaches the pager.
    private fun loadNextPage() {
        if (isFeedExhausted()) return
        val lastIndex = adapter.itemCount - 1
        if (lastIndex < 0) return

        if (mediaCountAtLoadRequest == null) {
            mediaCountAtLoadRequest = loadedMediaPosts().size
        }
        adapter.loadAround(lastIndex)
    }

    private fun continueLoadingUntilMediaArrives(mediaCount: Int) {
        val mediaCountAtRequest = mediaCountAtLoadRequest ?: return
        if (shouldKeepLoading(mediaCountAtRequest, mediaCount, isFeedExhausted())) {
            loadNextPage()
        } else {
            mediaCountAtLoadRequest = null
        }
    }

    // A failed page is not retried on its own: that would loop on an offline device.
    private fun isFeedExhausted(): Boolean {
        return appendLoadState.endOfPaginationReached || appendLoadState is LoadState.Error
    }

    private fun loadedMediaPosts(): List<PostEntity> = mediaPosts(adapter.snapshot(), isFilmstripMedia)

    private fun revealPost(postId: String?) {
        val index = adapterPositionOfPost(adapter.snapshot(), postId)
        val layoutManager = list.layoutManager as? LinearLayoutManager ?: return
        if (index < 0) return

        val isFullyVisible = index >= layoutManager.findFirstCompletelyVisibleItemPosition() &&
            index <= layoutManager.findLastCompletelyVisibleItemPosition()
        if (!isFullyVisible) {
            // Leave a quarter of the screen above the tile so it does not sit under the app bar.
            layoutManager.scrollToPositionWithOffset(index, list.height / REVEAL_OFFSET_DIVISOR)
        }
    }

    companion object {
        private const val REVEAL_OFFSET_DIVISOR = 4

        fun begin(
            holder: FilmstripFeedHolder,
            adapter: PostListAdapter,
            list: RecyclerView,
            isFilmstripMedia: (PostEntity) -> Boolean,
            lifecycleOwner: LifecycleOwner
        ): FilmstripFeedLink {
            val sessionId = holder.beginSession(mediaPosts(adapter.snapshot(), isFilmstripMedia))
            return FilmstripFeedLink(holder, sessionId, adapter, list, isFilmstripMedia, lifecycleOwner)
        }

        /** Reattaches a list whose view was recreated while its viewer session is still open. */
        fun resume(
            holder: FilmstripFeedHolder,
            sessionId: Int,
            adapter: PostListAdapter,
            list: RecyclerView,
            isFilmstripMedia: (PostEntity) -> Boolean,
            lifecycleOwner: LifecycleOwner
        ): FilmstripFeedLink {
            return FilmstripFeedLink(holder, sessionId, adapter, list, isFilmstripMedia, lifecycleOwner)
        }

        internal fun mediaPosts(
            adapterPosts: List<PostEntity?>,
            isFilmstripMedia: (PostEntity) -> Boolean
        ): List<PostEntity> {
            return adapterPosts.filterNotNull().filter(isFilmstripMedia)
        }

        /** Position of the post in the adapter, which counts the posts [mediaPosts] leaves out. */
        internal fun adapterPositionOfPost(adapterPosts: List<PostEntity?>, postId: String?): Int {
            return adapterPosts.indexOfFirst { it != null && it.id == postId }
        }

        /**
         * The viewer asked for more media; a page that added none leaves it with nothing new to
         * react to, so loading goes on until media arrives or the feed has no more pages.
         */
        internal fun shouldKeepLoading(
            mediaCountAtRequest: Int,
            mediaCountNow: Int,
            isFeedExhausted: Boolean
        ): Boolean {
            return mediaCountNow == mediaCountAtRequest && !isFeedExhausted
        }
    }
}
