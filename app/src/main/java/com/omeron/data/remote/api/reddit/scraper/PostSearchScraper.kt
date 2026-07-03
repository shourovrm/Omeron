package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.ListingData
import com.omeron.data.remote.api.reddit.model.PostChild
import com.omeron.data.remote.api.reddit.model.PostData
import com.omeron.data.remote.scraper.Scraper
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// ponytail: old.reddit's /search?type=link result cards (div.search-result-link) use a
// different markup shape than listing pages (div.thing, parsed by PostScraper) - title,
// score, subreddit, author and thumbnail are all present, but selftext/media/OC/spoiler/
// domain/url aren't in the card. Those fields stay at PostData's defaults; PostDetails
// re-fetches the full post via PostScraper when the card is tapped.
class PostSearchScraper(
    ioDispatcher: CoroutineDispatcher
) : RedditScraper<Listing>(ioDispatcher) {

    override suspend fun scrapDocument(document: Document): Listing {
        val posts = document.select("div.search-result-link")

        val children = posts.map { it.toPost() }
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

    private fun Element.toPost(): PostChild {
        val name = attr("data-fullname")

        val titleLink = selectFirst("a.search-title")
        val title = titleLink?.text().orEmpty()
        val titleHref = titleLink?.attr(Scraper.Selector.Attr.HREF).orEmpty()

        val thumbnailAnchor = selectFirst("a.thumbnail")
        val permalink = thumbnailAnchor?.attr(Scraper.Selector.Attr.HREF)?.takeIf { it.isNotBlank() }
            ?: titleHref.toHttpUrlOrNull()?.encodedPath
            ?: titleHref

        val isSelf = thumbnailAnchor?.hasClass("self") == true

        val thumbnail = thumbnailAnchor
            ?.selectFirst(Scraper.Selector.Tag.IMG)
            ?.attr(Scraper.Selector.Attr.SRC)
            ?.takeIf { it.isNotBlank() }
            ?.toValidLink()

        val isVideo = thumbnailAnchor?.selectFirst("div.duration-overlay") != null

        val prefixedSubreddit = selectFirst("a.search-subreddit-link")?.text().orEmpty()
        val subreddit = prefixedSubreddit.removePrefix("r/")

        val authorLink = selectFirst("span.search-author")?.selectFirst("a.author")
        val author = authorLink?.text() ?: "[deleted]"
        val distinguished = when {
            authorLink == null -> null
            authorLink.hasClass("moderator") -> "moderator"
            authorLink.hasClass("admin") -> "admin"
            else -> "regular"
        }

        val score = selectFirst("span.search-score")?.text().toCount()
        val commentsNumber = selectFirst("a.search-comments")?.text().toCount()

        val created = selectFirst("span.search-time")
            ?.selectFirst("time")
            ?.toTimeInSeconds() ?: 0L

        val flair = selectFirst("span.linkflairlabel")?.text()?.takeIf { it.isNotBlank() }

        val isOver18 = selectFirst("span.nsfw-stamp") != null || hasClass("over18")

        val postData = PostData(
            subreddit,
            emptyList(),
            null,
            title,
            prefixedSubreddit,
            name,
            null,
            0,
            false,
            flair,
            null,
            null,
            score,
            null,
            isSelf,
            null,
            "",
            null,
            null,
            false,
            isOver18,
            null,
            emptyList(),
            false,
            false,
            distinguished,
            author,
            commentsNumber,
            permalink,
            false,
            "",
            created,
            null,
            null,
            false,
            isVideo
        ).apply {
            this.thumbnail = thumbnail
            this.crosspost = null
        }

        return PostChild(postData)
    }

    private fun String?.toCount(): Int {
        return this?.substringBefore(" ")?.replace(",", "")?.toIntOrNull() ?: 0
    }

    companion object {
        private const val KIND = "t3"
    }
}
