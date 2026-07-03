package com.omeron.data.local.mapper

import com.omeron.data.model.Award
import com.omeron.data.model.Block
import com.omeron.data.model.Flair
import com.omeron.data.model.PosterType
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.PostEntity
import com.omeron.data.remote.api.reddit.model.PostData
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.util.HtmlParser
import com.omeron.util.extension.formatNumber
import com.omeron.util.extension.toMillis
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

@Singleton
class PostMapper2 @Inject constructor(
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
) : Mapper<PostData, PostEntity>(defaultDispatcher) {

    private val htmlParser: HtmlParser = HtmlParser(defaultDispatcher)

    override suspend fun toEntity(from: PostData): PostEntity {
        with(from) {
            val redditText = htmlParser.separateHtmlBlocks(selfTextHtml)
            val flair = Flair.fromData(linkFlairRichText, flair)
            return PostEntity(
                name,
                prefixedSubreddit,
                title,
                ratio?.run { round(ratio * 100).toInt() } ?: -1,
                totalAwards,
                isOC,
                flair,
                Flair.fromData(authorFlairRichText, authorFlair),
                isOver18 || isSpoiler || isOC || !flair.isEmpty() || isStickied || isArchived || isLocked,
                score.formatNumber(),
                postType,
                domain,
                isSelf,
                crossposts?.firstOrNull()?.let { toEntity(it) },
                selfTextHtml,
                Sorting(Sort.fromName(suggestedSort)),
                redditText,
                isOver18,
                previewUrl,
                (redditText.blocks.getOrNull(0)?.block as? Block.TextBlock)?.text,
                awardings.sortedByDescending { it.count }.map { Award(it.count, it.getIcon()) },
                isSpoiler,
                isArchived,
                isLocked,
                PosterType.fromDistinguished(distinguished),
                author,
                commentsNumber.formatNumber(),
                permalink,
                isStickied,
                url,
                created.toMillis(),
                mediaType,
                mediaUrl,
                gallery,
                seen = false,
                saved = false,
                crosspostScrap = crosspost
            )
        }
    }
}
