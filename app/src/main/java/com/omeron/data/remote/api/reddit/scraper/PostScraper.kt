package com.omeron.data.remote.api.reddit.scraper

import com.omeron.data.remote.api.reddit.model.Crosspost
import com.omeron.data.remote.api.reddit.model.GalleryData
import com.omeron.data.remote.api.reddit.model.GalleryDataItem
import com.omeron.data.remote.api.reddit.model.GalleryImage
import com.omeron.data.remote.api.reddit.model.GalleryItem
import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.ListingData
import com.omeron.data.remote.api.reddit.model.Media
import com.omeron.data.remote.api.reddit.model.MediaMetadata
import com.omeron.data.remote.api.reddit.model.PostChild
import com.omeron.data.remote.api.reddit.model.PostData
import com.omeron.data.remote.api.reddit.model.RedditVideoPreview
import com.omeron.data.remote.scraper.Scraper
import com.omeron.util.extension.toSeconds
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PostScraper(
    ioDispatcher: CoroutineDispatcher
) : RedditScraper<Listing>(ioDispatcher) {

    override suspend fun scrapDocument(document: Document): Listing {
        val posts = document.select(Selector.POST)
            .filter { element -> !element.attr(Selector.Attr.PROMOTED).toBoolean() }

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
        // subreddit
        val subreddit = attr(Selector.Attr.SUBREDDIT)

        val titleParagraph = selectFirst("p.title")
        // title
        val title = titleParagraph?.selectFirst(Scraper.Selector.Tag.A)?.text().orEmpty()

        // link_flair_richtext
        val flairRichText = titleParagraph
            ?.toRichFlairText()
            ?: emptyList()

        val flair = titleParagraph
            ?.selectFirst("span.linkflairlabel")
            ?.text()

        // subreddit_name_prefixed
        val prefixedSubreddit = attr(Selector.Attr.SUBREDDIT_PREFIXED)

        // name
        val name = attr(Selector.Attr.FULLNAME)

        // is_original_content
        val isOC = attr(Selector.Attr.OC).toBoolean()

        // score
        val score = attr(Selector.Attr.SCORE).toIntOrNull() ?: 0

        // domain
        val domain = attr(Selector.Attr.DOMAIN)

        // is_self
        val isSelf = hasClass(Selector.Class.SELF)

        // crosspost_parent_list
        val crosspostTitle = attr(Selector.Attr.CROSSPOST_ROOT_TITLE)
        val crosspostAuthor = attr(Selector.Attr.CROSSPOST_ROOT_AUTHOR)
        val crosspostSubredditPrefixed = attr(Selector.Attr.CROSSPOST_ROOT_SUBREDDIT_PREFIXED)
        val crosspostTime = attr(Selector.Attr.CROSSPOST_ROOT_TIME) // TODO: format = 1 year ago

        val crosspost = crosspostAuthor
            .takeIf { it.isNotBlank() }
            ?.run {
                Crosspost(crosspostAuthor, crosspostSubredditPrefixed, crosspostTitle, null)
            }

        // over_18
        val isOver18 = attr(Selector.Attr.NSFW).toBoolean()

        // spoiler
        val isSpoiler = attr(Selector.Attr.SPOILER).toBoolean()

        // locked
        val isLocked = hasClass(Selector.Class.LOCKED)

        // num_comments
        val commentsNumber = attr(Selector.Attr.COMMENT_COUNT).toIntOrNull() ?: 0

        // permalink
        val permalink = attr(Selector.Attr.PERMALINK)

        // stickied
        val isStickied = hasClass(Selector.Class.STICKIED)

        // url
        val url = attr(Selector.Attr.URL)

        // created_utc
        val created = attr(Selector.Attr.TIMESTAMP).toLongOrNull()?.toSeconds() ?: 0L

        // is_gallery
        val isRedditGallery = attr(Selector.Attr.IS_GALLERY).toBoolean()

        val thumbnailClass = selectFirst("a.thumbnail")
        // is_video: the thumbnail's duration-overlay is only reliably present on listing pages,
        // not the single-post page, so also treat a v.redd.it domain as video - otherwise a
        // reddit-hosted video opened via permalink (e.g. from search) is never detected and its
        // DASH/HLS url is never read.
        val isVideo = thumbnailClass?.selectFirst("div.duration-overlay") != null ||
            domain.contains("v.redd.it")
        val thumbnail = thumbnailClass
            ?.selectFirst(Scraper.Selector.Tag.IMG)
            ?.attr(Scraper.Selector.Attr.SRC)
            ?.toValidLink()

        // Listing pages ship the post's media as a `data-cachedhtml` string on the expando; the
        // single-post page (what getPost fetches) renders that same markup INLINE in the expando,
        // with no cachedhtml attr. Parse cachedhtml when present, else read the live expando -
        // both carry the same gallery tiles / video data-urls, so galleries and videos parse in
        // either context (previously post-page opens got no media -> blank gallery / black video).
        val expandoElement = selectFirst("div.expando")
        val expando = expandoElement
            ?.attr(Selector.Attr.CACHED_HTML)
            ?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it) }
            ?: expandoElement

        val media = when {
            isVideo -> {
                // Real signed DASH/HLS url is in the expando's cachedhtml. The old
                // hardcoded "$url/DASH_720.mp4" guess is unsigned -> Reddit CDN 403s it.
                // The SIGNED DASH/HLS manifest url is required (unsigned "$url/DASH_720.mp4" 403s).
                // Listing pages carry it inside the expando's cachedhtml; the single-post page puts
                // it on a live <div id="video-*" data-mpd-url=... data-hls-url=...> that sits OUTSIDE
                // the (uninitialized) expando - so search the whole post element too. Prefer DASH
                // (.mpd): that's the form the app's working subreddit videos use, and ExoPlayer's
                // HLS path doesn't survive R8 in the release build. HLS only as a last resort.
                val videoUrl = expando?.selectFirst("[data-mpd-url]")?.attr("data-mpd-url")?.ifBlank { null }
                    ?: selectFirst("[data-mpd-url]")?.attr("data-mpd-url")?.ifBlank { null }
                    ?: expando?.selectFirst("[data-hls-url]")?.attr("data-hls-url")?.ifBlank { null }
                    ?: selectFirst("[data-hls-url]")?.attr("data-hls-url")?.ifBlank { null }
                    ?: "$url/DASHPlaylist.mpd"
                Media(
                    null,
                    null,
                    RedditVideoPreview(
                        videoUrl,
                        0,
                        0,
                        0,
                        false
                    )
                )
            }

            expando != null -> expando.toMedia()

            else -> null
        }

        val galleryData = expando?.toGalleryData()
        val mediaMetadata = expando?.toMediaMetadata()

        val selfTextHtml = selectFirst("div.usertext-body")
            ?.selectFirst(Selector.MD)?.outerHtml()

        val tagline = getTagline()

        val postData = PostData(
            subreddit,
            flairRichText,
            authorFlairRichText = null,
            title,
            prefixedSubreddit,
            name,
            null,
            tagline.totalAwards,
            isOC,
            flair,
            null,
            galleryData,
            score,
            null,
            isSelf,
            null,
            domain,
            selfTextHtml,
            null,
            false,
            isOver18,
            null,
            tagline.awardings,
            isSpoiler,
            isLocked,
            tagline.distinguished,
            tagline.author,
            commentsNumber,
            permalink,
            isStickied,
            url,
            created,
            media,
            mediaMetadata,
            isRedditGallery,
            isVideo
        ).apply {
            this.thumbnail = thumbnail
            this.crosspost = crosspost
        }

        return PostChild(postData)
    }

    private fun Element.toMedia(): Media? {
        val source = selectFirst("source") ?: return null

        return when (source.attr("type")) {
            "video/mp4" -> {
                val src = source.attr(Scraper.Selector.Attr.SRC)

                Media(
                    null,
                    null,
                    RedditVideoPreview(
                        src,
                        0,
                        0,
                        0,
                        true
                    )
                )
            }

            else -> null
        }
    }

    private fun Element.toGalleryData(): GalleryData {
        val items = select("div.gallery-tile")
            .map {
                val id = it.attr(Selector.Attr.MEDIA_ID)
                GalleryDataItem(null, id)
            }

        return GalleryData(items)
    }

    private fun Element.toMediaMetadata(): MediaMetadata? {
        val items = select("div.gallery-preview")
            .map {
                val id = it.attr(Scraper.Selector.Attr.ID).substringAfterLast("-")
                val src = it.selectFirst("div.media-preview-content")
                    ?.selectFirst(Scraper.Selector.Tag.A)
                    ?.attr(Scraper.Selector.Attr.HREF)

                val image = GalleryImage(0, 0, src, null)

                GalleryItem(null, image, null, id)
            }

        return if (items.isNotEmpty()) MediaMetadata(items) else null
    }

    companion object {
        private const val KIND = "t3"
    }
}
