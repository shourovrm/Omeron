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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.ceil

class SmartPostListDataSource(
    private val source: CurrentSource,
    private val query: List<String>,
    private val sorting: Sorting,
    private val defaultDispatcher: CoroutineDispatcher
) : PagingSource<List<String>, Child>() {

    private val joinedQuery by lazy { RedditUtil.joinSubredditList(query) }
    private val chunkSize by lazy {
        // Find the optimal chunk size to have lists of similar sizes
        ceil(query.size / ceil(query.size / REDDIT_SUBREDDIT_LIMIT.toDouble())).toInt()
    }

    override val keyReuseSupported: Boolean = true

    override suspend fun load(params: LoadParams<List<String>>): LoadResult<List<String>, Child> {
        return try {
            if (query.size > REDDIT_SUBREDDIT_LIMIT) getSmartData(params) else getData(params)
        } catch (exception: IOException) {
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        } catch (exception: JsonDataException) {
            LoadResult.Error(exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Scraper failures (malformed HTML, missing nodes) are not IO errors but must not
            // take the app down.
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<List<String>, Child>): List<String>? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey
        }
    }

    private suspend fun getSmartData(
        params: LoadParams<List<String>>
    ): LoadResult<List<String>, Child> {
        val queries = withContext(defaultDispatcher) {
            query
                // Step 1: Split the subreddit list into chunks
                .chunked(chunkSize)
                // Step 2: Create the query string for each chunk
                .map { RedditUtil.joinSubredditList(it) }
                // Step 3: Map each chunk with its `after` key (if available)
                .mapIndexed { index, chunkedList -> chunkedList to params.key?.getOrNull(index) }
        }

        // Step 4: Request the posts for each chunk in parallel. A chunk whose last page returned
        // no `after` is stored as "" in the key and is skipped: requesting it again with a null
        // `after` would return its first page.
        val isFirstLoad = params.key == null
        val responses = coroutineScope {
            queries
                .map { (chunkQuery, after) ->
                    async {
                        if (!isFirstLoad && after.isNullOrEmpty()) return@async null
                        source.getSubreddit(
                            chunkQuery,
                            sorting.generalSorting,
                            sorting.timeSorting,
                            after
                        )
                    }
                }
                .awaitAll()
        }

        // Step 5: Flatten (and sort) the responses in order to have a single list of posts
        val data = withContext(defaultDispatcher) {
            responses
                .map { it?.data?.children.orEmpty() }
                .sort(sorting)
        }

        // Step 6: Retrieve the `after` key for each response and create a list out of them.
        // Once every chunk is exhausted there is no next page.
        val after = responses.map { it?.data?.after.orEmpty() }
        val nextKey = if (after.all { it.isEmpty() }) null else after

        return LoadResult.Page(data, null, nextKey)
    }

    private suspend fun getData(params: LoadParams<List<String>>): LoadResult<List<String>, Child> {
        val response = source.getSubreddit(
            joinedQuery,
            sorting.generalSorting,
            sorting.timeSorting,
            params.key?.getOrNull(0)
        )

        val data = response.data

        return LoadResult.Page(data.children, null, data.after?.let { listOf(it) })
    }

    private fun List<List<Child>>.sort(sorting: Sorting): List<Child> {
        return when(sorting.generalSorting) {
            // If sorting is set to NEW, simply flatten the lists and sort the posts by date
            Sort.NEW -> this.flatten().sortedByDescending { (it as PostChild).data.created }
            // If sorting is set to TOP, simply flatten the lists and sort the posts by score
            Sort.TOP -> this.flatten().sortedByDescending { (it as PostChild).data.score }
            // For all the other sorting methods, interlace the lists to have a consistent result
            // [['a', 'b', 'c'], ['e', 'f', 'g'], ['h', 'i']] ==> ['a', 'e', 'h', 'b', 'f', 'i', 'c', 'g']
            else -> this.interlace()
        }
    }

    companion object {
        private const val REDDIT_SUBREDDIT_LIMIT = 100
    }
}
