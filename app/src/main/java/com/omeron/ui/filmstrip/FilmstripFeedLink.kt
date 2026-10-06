package com.omeron.ui.filmstrip

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omeron.data.model.db.PostEntity
import com.omeron.ui.postlist.PostListAdapter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The feed list's side of the Filmstrip hand-off: publishes the adapter's loaded posts to the
 * [FilmstripFeedHolder], loads another page when the viewer asks, and scrolls the grid to the
 * last viewed tile when the viewer closes. Lives as long as the list's view.
 */
class FilmstripFeedLink private constructor(
    private val holder: FilmstripFeedHolder,
    val sessionId: Int,
    private val adapter: PostListAdapter,
    private val list: RecyclerView,
    lifecycleOwner: LifecycleOwner
) {

    private val publishLoadedPosts: () -> Unit = {
        holder.publish(sessionId, loadedPosts(adapter))
    }

    init {
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
        adapter.removeOnPagesUpdatedListener(publishLoadedPosts)
    }

    // Paging loads the next page when an item near the end is read, so reading the last one is
    // how the viewer's request reaches the pager.
    private fun loadNextPage() {
        val lastIndex = adapter.itemCount - 1
        if (lastIndex >= 0) adapter.loadAround(lastIndex)
    }

    private fun revealPost(postId: String?) {
        val index = loadedPosts(adapter).indexOfFirst { it.id == postId }
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
            lifecycleOwner: LifecycleOwner
        ): FilmstripFeedLink {
            val sessionId = holder.beginSession(loadedPosts(adapter))
            return FilmstripFeedLink(holder, sessionId, adapter, list, lifecycleOwner)
        }

        /** Reattaches a list whose view was recreated while its viewer session is still open. */
        fun resume(
            holder: FilmstripFeedHolder,
            sessionId: Int,
            adapter: PostListAdapter,
            list: RecyclerView,
            lifecycleOwner: LifecycleOwner
        ): FilmstripFeedLink {
            return FilmstripFeedLink(holder, sessionId, adapter, list, lifecycleOwner)
        }

        private fun loadedPosts(adapter: PostListAdapter): List<PostEntity> {
            return adapter.snapshot().filterNotNull()
        }
    }
}
