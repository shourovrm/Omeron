package com.omeron.data.local.mapper

import com.omeron.data.model.Block
import com.omeron.data.model.Comment.CommentEntity
import com.omeron.data.model.RedditText
import com.omeron.data.model.SavedItem
import com.omeron.data.model.db.History
import com.omeron.data.model.db.PostEntity
import com.omeron.di.DispatchersModule
import com.omeron.util.HtmlParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedMapper2 @Inject constructor(
    @DispatchersModule.DefaultDispatcher defaultDispatcher: CoroutineDispatcher
) : Mapper<Any, SavedItem>(defaultDispatcher) {

    private val htmlParser: HtmlParser = HtmlParser(defaultDispatcher)

    // The saved lists are mapped again on every database change. Parsing is the slow part and
    // a row's HTML does not change, so each text is parsed once.
    private val parsedTexts = ConcurrentHashMap<String, RedditText>()

    private suspend fun parsed(html: String?): RedditText {
        if (html == null) return htmlParser.separateHtmlBlocks(null)
        return parsedTexts[html] ?: htmlParser.separateHtmlBlocks(html).also { parsedTexts[html] = it }
    }

    override suspend fun toEntity(from: Any): SavedItem {
        throw UnsupportedOperationException()
    }

    override suspend fun toEntities(from: List<Any>): List<SavedItem> {
        throw UnsupportedOperationException()
    }

    suspend fun dataToEntity(data: PostEntity): SavedItem = withContext(defaultDispatcher) {
        val redditText = parsed(data.selfTextHtml)
        SavedItem.Post(
            data.apply {
                hasFlairs = isOver18 || isSpoiler || isOC || isStickied || isArchived || isLocked
                selfRedditText = redditText
                previewText = (redditText.blocks.firstOrNull()?.block as? Block.TextBlock)?.text
            }
        )
    }

    suspend fun dataToEntity(data: CommentEntity): SavedItem = withContext(defaultDispatcher) {
        SavedItem.Comment(
            data.apply {
                body = parsed(bodyHtml)
            }
        )
    }

    suspend fun postsToEntities(data: List<PostEntity>): List<SavedItem> = withContext(
        defaultDispatcher
    ) {
        data.map { dataToEntity(it) }
    }

    suspend fun commentsToEntities(data: List<CommentEntity>): List<SavedItem> =
        withContext(defaultDispatcher) {
            data.map { dataToEntity(it) }
        }

    suspend fun historyToEntities(data: List<History>, savedIds: List<String>): List<SavedItem> =
        withContext(defaultDispatcher) {
            data.map { dataToEntity(it.toPostEntity(saved = savedIds.contains(it.postId))) }
        }
}
