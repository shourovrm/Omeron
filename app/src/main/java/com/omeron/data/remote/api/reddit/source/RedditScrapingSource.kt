package com.omeron.data.remote.api.reddit.source

import com.omeron.data.model.Sort
import com.omeron.data.model.TimeSorting
import com.omeron.data.remote.api.reddit.RedditApi
import com.omeron.data.remote.api.reddit.model.AboutUserChild
import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.data.remote.api.reddit.model.Data
import com.omeron.data.remote.api.reddit.model.JsonMore
import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.ListingData
import com.omeron.data.remote.api.reddit.model.MoreChildren
import com.omeron.data.remote.api.reddit.model.UNSIGNED_DASH_SUFFIX
import com.omeron.data.remote.api.reddit.scraper.CommentScraper
import com.omeron.data.remote.api.reddit.scraper.CommunitySearchScraper
import com.omeron.data.remote.api.reddit.scraper.PostScraper
import com.omeron.data.remote.api.reddit.scraper.PostSearchScraper
import com.omeron.data.remote.api.reddit.scraper.SubredditScraper
import com.omeron.data.remote.api.reddit.scraper.SubredditSearchScraper
import com.omeron.data.remote.api.reddit.scraper.UserScaper
import com.omeron.di.DispatchersModule.IoDispatcher
import com.omeron.di.DispatchersModule.MainImmediateDispatcher
import com.omeron.di.NetworkModule.RedditScrap
import retrofit2.HttpException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject
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
        // The subreddit's search page lists results with the same markup as the site-wide one.
        val response = redditApi.searchInSubreddit(subreddit, query, sort, timeSorting, after)
        return PostSearchScraper(ioDispatcher).scrap(response.string())
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

    /**
     * The single-post HTML page carries no signed DASH manifest for v.redd.it, so
     * [PostScraper] falls back to an unsigned [UNSIGNED_DASH_SUFFIX] guess the CDN 403s.
     * Pull the signed dash_url from the permalink's public .json instead (no auth). Kept apart
     * from [getPost] so the page does not wait for this second request.
     */
    override suspend fun getSignedVideoUrl(permalink: String, sort: Sort): String? {
        val json = redditApi.getPost("${permalink.trimEnd('/')}.json", 1, sort).string()
        return DASH_URL_REGEX.find(json)?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?.replace("\\u0026", "&")
            ?.replace("&amp;", "&")
    }

    /**
     * old.reddit's /api/morechildren (no .json suffix - reddit 403s that variant) returns a
     * JSON envelope whose "things" each carry the comment as an escaped HTML fragment.
     * Parse the fragments with [CommentScraper]; depths are rebuilt from the parent chain,
     * rooted at [depth] (the depth of the tapped "More" stub).
     */
    override suspend fun getMoreChildren(
        children: String,
        linkId: String,
        depth: Int
    ): MoreChildren {
        val response = redditApi.getMoreChildren(children, linkId)
        // Empty children (a stale/empty stub) yields {"json": {"errors": []}} - no data key.
        val things = JSONObject(response.string())
            .getJSONObject("json")
            .optJSONObject("data")
            ?.optJSONArray("things")
            ?: JSONArray()

        val ids = mutableListOf<String>()
        val parents = mutableListOf<String>()
        val contents = mutableListOf<String>()
        for (i in 0 until things.length()) {
            val data = things.getJSONObject(i).getJSONObject("data")
            ids.add(data.optString("id"))
            parents.add(data.optString("parent"))
            contents.add(data.optString("content"))
        }

        // Things come parents-first, so a child's parent depth is always already known.
        val depthById = mutableMapOf<String, Int>()
        val depths = ids.mapIndexed { index, id ->
            val d = depthById[parents[index]]?.plus(1) ?: depth
            depthById[id] = d
            d
        }

        val comments = CommentScraper(ioDispatcher)
            .scrapMoreComments(contents, depths, parents, linkId)

        return MoreChildren(JsonMore(Data(comments)))
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

    // old.reddit's /search?type=user renders no user rows at all (confirmed against a live
    // sample - only an empty "people" facet). Best scraping can do is an exact-username
    // lookup against the profile page, so a query matching a real account returns one row.
    override suspend fun searchUser(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        val children = runCatching { getUserInfo(query.trim().removePrefix("u/")) }
            .getOrNull()
            ?.takeIf { (it as? AboutUserChild)?.data?.name?.isNotBlank() == true }
            ?.let { listOf(it) }
            ?: emptyList()

        return Listing("t2", ListingData(null, null, children, null, null))
    }

    override suspend fun searchSubreddit(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?
    ): Listing {
        // old.reddit's community search returns nothing for many plain words ("funny"), so new
        // Reddit's page is asked first. It has no second page; old.reddit serves the rest, and
        // the whole search when new Reddit's page fails or its markup changes.
        if (after == null) {
            val communities = searchCommunitiesOnNewReddit(query)
            if (communities.data.children.isNotEmpty()) return communities
        }
        val response = redditApi.searchSubreddit(query, sort, timeSorting, after)
        return SubredditSearchScraper(ioDispatcher).scrap(response.string())
    }

    private suspend fun searchCommunitiesOnNewReddit(query: String): Listing {
        val response = try {
            redditApi.searchCommunities(query)
        } catch (exception: HttpException) {
            return Listing("t5", ListingData(null, null, emptyList(), null, null))
        }
        return CommunitySearchScraper(ioDispatcher).scrap(response.string())
    }

    companion object {
        private val DASH_URL_REGEX = Regex("\"dash_url\"\\s*:\\s*\"([^\"]+)\"")
    }
}
