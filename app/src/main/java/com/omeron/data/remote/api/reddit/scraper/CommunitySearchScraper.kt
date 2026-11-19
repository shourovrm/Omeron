package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.AboutChild
import com.omeron.data.remote.api.reddit.model.AboutData
import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.ListingData
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Reads the communities from www.reddit.com/search/?type=communities. That page finds
 * communities old.reddit's search misses, but it holds the first results only: the rest is
 * loaded by a script, so there is no next page.
 */
class CommunitySearchScraper(
    ioDispatcher: CoroutineDispatcher
) : RedditScraper<Listing>(ioDispatcher) {

    override suspend fun scrapDocument(document: Document): Listing {
        val children = document.select("[data-testid=search-community]")
            .mapNotNull { it.toSubreddit() }

        return Listing(KIND, ListingData(null, null, children, null, null))
    }

    private fun Element.toSubreddit(): AboutChild? {
        val name = selectFirst("shreddit-join-button")?.attr("name").orEmpty()
        if (name.isEmpty()) return null

        // The page has no community title, so the description takes its line in the result.
        val description = selectFirst("[data-testid=search-subreddit-desc-text]")?.text().orEmpty()

        val icon = selectFirst("img.shreddit-subreddit-icon__icon")?.attr("src")

        val over18 = selectFirst("search-telemetry-tracker")
            ?.attr("data-faceplate-tracking-context")
            .orEmpty()
            .contains(NSFW_MARKER)

        val data = AboutData(
            null,
            name,
            null,
            description,
            null,
            null,
            icon,
            null,
            null,
            null,
            "",
            "",
            null,
            null,
            over18,
            null,
            "https://old.reddit.com/r/$name/",
            0L
        )

        return AboutChild(data)
    }

    companion object {
        private const val KIND = "t5"

        private const val NSFW_MARKER = "\"nsfw\":true"
    }
}
