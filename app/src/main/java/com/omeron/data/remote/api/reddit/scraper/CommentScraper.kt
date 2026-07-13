package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.data.remote.api.reddit.model.CommentChild
import com.omeron.data.remote.api.reddit.model.CommentData
import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.ListingData
import com.omeron.data.remote.api.reddit.model.MoreChild
import com.omeron.data.remote.api.reddit.model.MoreData
import com.omeron.data.remote.scraper.Scraper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

class CommentScraper(
    private val ioDispatcher: CoroutineDispatcher
) : RedditScraper<Listing>(ioDispatcher) {

    private var linkId: String = ""

    /**
     * Parses /api/morechildren "things": each [contents] entry is one comment's
     * entity-escaped old-reddit HTML fragment, flat (parents before children), with its
     * absolute depth precomputed in [depths] and its parent fullname in [parents].
     */
    suspend fun scrapMoreComments(
        contents: List<String>,
        depths: List<Int>,
        parents: List<String>,
        linkId: String
    ): List<Child> = withContext(ioDispatcher) {
        this@CommentScraper.linkId = linkId

        contents.mapIndexedNotNull { index, content ->
            val html = Parser.unescapeEntities(content, false)
            val thing = Jsoup.parse(html)
                .selectFirst("div.${Selector.Class.THING}")
                ?: return@mapIndexedNotNull null

            when {
                thing.hasClass(Selector.Class.MORE_CHILDREN) ->
                    thing.toMore(depths[index], parents[index])
                !thing.hasClass(Selector.Class.MORE_RECURSION) ->
                    thing.toComment(depths[index])
                else -> null
            }
        }
    }

    override suspend fun scrapDocument(document: Document): Listing {
        val post = document.selectFirst(Selector.POST)
            ?.attr(Selector.Attr.FULLNAME)

        linkId = post.orEmpty()

        val after = getNextKey()

        val siteTable = document.selectFirst(Selector.SITETABLE_NESTED)
            ?: document.selectFirst(Selector.SITETABLE_LINK)

        return siteTable
            ?.run { getComments(0) }
            .run {
                Listing(
                    KIND,
                    ListingData(
                        null,
                        null,
                        this ?: emptyList(),
                        after,
                        null
                    )
                )
            }
    }

    private fun Element.getComments(depth: Int, parentName: String? = null): List<Child> {
        return children()
            .filter { element -> element.hasClass(Selector.Class.THING) }
            .mapNotNull { comment ->
                when {
                    comment.hasClass(Selector.Class.MORE_CHILDREN) -> {
                        comment.toMore(depth, parentName)
                    }
                    !comment.hasClass(Selector.Class.MORE_RECURSION) -> comment.toComment(depth)
                    else -> null
                }
            }
    }

    private fun Element.toComment(depth: Int): CommentChild {
        val entry = selectFirst("div.entry")

        val bodyHtml = entry?.selectFirst(Selector.MD)?.outerHtml().orEmpty()

        val name = attr(Selector.Attr.FULLNAME)

        val parent = selectFirst("p.parent")
        val parentTitle = parent?.selectFirst("a.title")
        val linkTitle = parentTitle?.text()
        val linkPermalink = parentTitle?.attr(Scraper.Selector.Attr.HREF)
        val linkAuthor = parent?.selectFirst("a.author")?.text()

        val children = selectFirst(Selector.SITETABLE)
        val comments = children
            ?.run { this.getComments(depth + 1, name) }
            ?.run { Listing(KIND, ListingData(null, null, this, null, null)) }

        val permalink = attr(Selector.Attr.PERMALINK)

        val controversiality = if (hasClass(Selector.Class.CONTROVERSIAL)) 1 else 0

        val subreddit = attr(Selector.Attr.SUBREDDIT)

        val tagline = getTagline()

        val data = CommentData(
            tagline.totalAwards,
            tagline.flairRichText,
            linkId,
            comments,
            tagline.author,
            tagline.score,
            tagline.awardings,
            bodyHtml,
            tagline.edited,
            tagline.isSubmitter,
            tagline.stickied,
            tagline.scoreHidden,
            permalink,
            name,
            name,
            tagline.created,
            controversiality,
            tagline.flair,
            depth,
            tagline.distinguished,
            subreddit,
            linkTitle,
            linkPermalink,
            linkAuthor
        )

        return CommentChild(data)
    }

    private fun Element.toMore(depth: Int, parentName: String?): MoreChild {
        val moreComments = selectFirst("span.morecomments")
            ?.selectFirst(Scraper.Selector.Tag.A)
            ?.attr("onclick")
            ?.run {
                MORE_REGEX.find(this)?.groups?.get(1)?.value
                    ?: MORE_REGEX_LEGACY.find(this)?.groups?.get(1)?.value
            }
            ?.run { split(',') }
            ?: emptyList()

        val name = attr(Selector.Attr.FULLNAME)

        val data = MoreData(
            moreComments.size,
            name,
            name,
            depth,
            parentName.orEmpty(),
            moreComments.toMutableList()
        )

        return MoreChild(data)
    }

    companion object {
        private const val KIND = "t1"

        // onclick="return morechildren(this, 't3_xxx', 'confidence', 'id1,id2', 'False')"
        // -> capture the 4th arg (comma-joined child ids). Old markup used 'sort:ids'.
        private val MORE_REGEX =
            Regex("morechildren\\(this,\\s*'[^']*',\\s*'[^']*',\\s*'([^']+)'")
        private val MORE_REGEX_LEGACY = Regex("'\\w+:(.*?)'")
    }
}
