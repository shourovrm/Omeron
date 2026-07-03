package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.AboutUserChild
import com.omeron.data.remote.api.reddit.model.AboutUserData
import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.ListingData
import com.omeron.data.remote.scraper.Scraper
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// ponytail: old.reddit's /search?type=user result rows only expose a username and a
// karma count (no subreddit/about/created data - that needs a separate per-user
// /user/{name}/about request, i.e. N+1). Unavailable AboutUserData fields are left at
// their defaults; upgrade path is to hit UserScaper per result if accurate profile data
// (over18, icon, description) becomes worth the extra requests.
class UserSearchScraper(
    ioDispatcher: CoroutineDispatcher
) : RedditScraper<Listing>(ioDispatcher) {

    override suspend fun scrapDocument(document: Document): Listing {
        val users = document.select("div.search-result-user")

        val children = users.mapNotNull { it.toUser() }
        val after = getNextKey()

        return Listing(
            KIND,
            ListingData(
                null,
                null,
                children,
                after,
                null
            )
        )
    }

    private fun Element.toUser(): AboutUserChild? {
        val link = selectFirst("a.author") ?: selectFirst("a[href*=/user/]")
        val href = link?.attr(Scraper.Selector.Attr.HREF).orEmpty()

        val name = USER_NAME_REGEX.find(href)?.groupValues?.get(1)
            ?: link?.text()?.removePrefix("/u/")?.removePrefix("u/")

        if (name.isNullOrBlank()) return null

        val karma = selectFirst("span.karma")?.toInt() ?: -1

        val data = AboutUserData(
            isSuspended = false,
            isEmployee = false,
            subreddit = null,
            id = null,
            iconImg = null,
            linkKarma = karma,
            totalKarma = karma,
            name = name,
            created = 0L,
            snoovatarImg = null,
            commentKarma = -1
        )

        return AboutUserChild(data)
    }

    companion object {
        private const val KIND = "t2"

        private val USER_NAME_REGEX = Regex("/user/([^/]+)")
    }
}
