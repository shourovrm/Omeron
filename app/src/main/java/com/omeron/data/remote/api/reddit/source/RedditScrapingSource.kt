package com.omeron.data.remote.api.reddit.source

import com.omeron.data.model.Sort
import com.omeron.data.model.TimeSorting
import com.omeron.data.remote.api.reddit.RedditApi
import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.data.remote.api.reddit.model.Data
import com.omeron.data.remote.api.reddit.model.JsonMore
import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.ListingData
import com.omeron.data.remote.api.reddit.model.MoreChildren
import com.omeron.data.remote.api.reddit.scraper.CommentScraper
import com.omeron.data.remote.api.reddit.scraper.PostScraper
import com.omeron.data.remote.api.reddit.scraper.PostSearchScraper
import com.omeron.data.remote.api.reddit.scraper.SubredditScraper
import com.omeron.data.remote.api.reddit.scraper.SubredditSearchScraper
import com.omeron.data.remote.api.reddit.scraper.UserScaper
import com.omeron.di.DispatchersModule.IoDispatcher
import com.omeron.di.DispatchersModule.MainImmediateDispatcher
import com.omeron.di.NetworkModule.RedditScrap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditScrapingSource @Inject constructor(
    @RedditScrap private val redditApi: RedditApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher private val mainImmediateDispatcher: CoroutineDispatcher
) : BaseRedditSource {

    private val scope = CoroutineScope(mainImmediateDispatcher + SupervisorJob())

    override suspend fun getSubreddit(
        subreddit: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        val response = redditApi.getSubreddit(subreddit, sort, timeSorting, after)
        return PostScraper(ioDispatcher).scrap(response.string())
    }

    override suspend fun getSubredditInfo(subreddit: String): Child {
        val response = redditApi.getSubreddit(subreddit, Sort.HOT, null)
        return SubredditScraper(ioDispatcher).scrap(response.string())
    }

    override suspend fun searchInSubreddit(
        subreddit: String,
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        // TODO
        return Listing("t3", ListingData(null, null, emptyList(), null, null))
    }

    override suspend fun getPost(permalink: String, limit: Int?, sort: Sort): List<Listing> {
        val response = redditApi.getPost(permalink, limit, sort)
        val body = response.string()

        val post = scope.async {
            PostScraper(ioDispatcher).scrap(body)
        }

        val comments = scope.async {
            CommentScraper(ioDispatcher).scrap(body)
        }

        return listOf(post.await(), comments.await())
    }

    override suspend fun getMoreChildren(children: String, linkId: String): MoreChildren {
        // TODO
        return MoreChildren(JsonMore(Data(emptyList())))
    }

    override suspend fun getUserInfo(user: String): Child {
        val response = redditApi.getUserPosts(user, Sort.HOT, null)
        return UserScaper(ioDispatcher).scrap(response.string())
    }

    override suspend fun getUserPosts(
        user: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        val response = redditApi.getUserPosts(user, sort, timeSorting, after)
        return PostScraper(ioDispatcher).scrap(response.string())
    }

    override suspend fun getUserComments(
        user: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        val response = redditApi.getUserComments(user, sort, timeSorting, after)
        return CommentScraper(ioDispatcher).scrap(response.string())
    }

    override suspend fun searchPost(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        val response = redditApi.searchPost(query, sort, timeSorting, after)
        return PostSearchScraper(ioDispatcher).scrap(response.string())
    }

    // ponytail: old.reddit's /search?type=user renders no user rows at all (confirmed
    // against a live sample - only an empty "people" facet), so it can't be scraped without
    // the API. Stub satisfies BaseRedditSource; unreachable from UI (Users tab removed).
    override suspend fun searchUser(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        return Listing("t2", ListingData(null, null, emptyList(), null, null))
    }

    override suspend fun searchSubreddit(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        val response = redditApi.searchSubreddit(query, sort, timeSorting, after)
        return SubredditSearchScraper(ioDispatcher).scrap(response.string())
    }
}
