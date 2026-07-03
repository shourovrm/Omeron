package com.omeron.data.remote.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.data.remote.api.reddit.model.PostChild
import com.omeron.data.remote.api.reddit.source.CurrentSource
import com.omeron.util.RedditUtil
import com.omeron.util.extension.interlace
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.ceil

/**
 * Merged feed for a multireddit: subreddits (chunked, like [SmartPostListDataSource]) plus
 * followed users, fetched in parallel and merged with the same sort rules.
 *
 * The `after` key list is laid out as: [subreddit-chunk-0, ..., subreddit-chunk-N, user-0, ...,
 * user-M] so each source keeps its own pagination cursor across loads.
 */
class MultiredditDataSource(
    private val source: CurrentSource,
    private val subreddits: List<String>,
    private val users: List<String>,
    private val sorting: Sorting,
    private val defaultDispatcher: CoroutineDispatcher,
    mainImmediateDispatcher: CoroutineDispatcher
) : PagingSource<List<String>, Child>() {

    private val scope = CoroutineScope(mainImmediateDispatcher + SupervisorJob())

    private val chunkSize by lazy {
        if (subreddits.isEmpty()) 1 else {
            ceil(subreddits.size / ceil(subreddits.size / REDDIT_SUBREDDIT_LIMIT.toDouble())).toInt()
        }
    }

    private val subredditChunks by lazy {
        subreddits.chunked(chunkSize).map { RedditUtil.joinSubredditList(it) }
    }

    override val keyReuseSupported: Boolean = true

    override suspend fun load(params: LoadParams<List<String>>): LoadResult<List<String>, Child> {
        return try {
            getData(params)
        } catch (exception: IOException) {
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        } catch (exception: JsonDataException) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<List<String>, Child>): List<String>? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey
        }
    }

    private suspend fun getData(params: LoadParams<List<String>>): LoadResult<List<String>, Child> {
        // Empty multireddit (no subreddits, no users) -> nothing to page.
        if (subredditChunks.isEmpty() && users.isEmpty()) {
            return LoadResult.Page(emptyList(), null, null)
        }

        // Subreddit chunks occupy key slots [0, subredditChunks.size), users occupy the rest.
        val subredditJobs = subredditChunks.mapIndexed { index, chunk ->
            scope.async {
                source.getSubreddit(chunk, sorting.generalSorting, sorting.timeSorting, params.key?.getOrNull(index))
            }
        }
        val userJobs = users.mapIndexed { index, user ->
            val keyIndex = subredditChunks.size + index
            scope.async {
                source.getUserPosts(user, sorting.generalSorting, sorting.timeSorting, params.key?.getOrNull(keyIndex))
            }
        }

        val responses = (subredditJobs + userJobs).awaitAll()

        val data = withContext(defaultDispatcher) {
            responses.map { it.data.children }.sort(sorting)
        }

        val after = withContext(defaultDispatcher) {
            responses.map { it.data.after ?: "" }
        }

        return LoadResult.Page(data, null, after)
    }

    private fun List<List<Child>>.sort(sorting: Sorting): List<Child> {
        return when (sorting.generalSorting) {
            Sort.NEW -> this.flatten().sortedByDescending { (it as PostChild).data.created }
            Sort.TOP -> this.flatten().sortedByDescending { (it as PostChild).data.score }
            else -> this.interlace()
        }
    }

    companion object {
        private const val REDDIT_SUBREDDIT_LIMIT = 100
    }
}
