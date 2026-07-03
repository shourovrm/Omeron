package com.omeron.data.local.mapper

import com.omeron.data.model.Award
import com.omeron.data.model.Comment
import com.omeron.data.model.Flair
import com.omeron.data.model.PosterType
import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.data.remote.api.reddit.model.ChildType
import com.omeron.data.remote.api.reddit.model.CommentChild
import com.omeron.data.remote.api.reddit.model.CommentData
import com.omeron.data.remote.api.reddit.model.MoreChild
import com.omeron.data.remote.api.reddit.model.MoreData
import com.omeron.data.remote.api.reddit.model.PostData
import com.omeron.di.DispatchersModule
import com.omeron.util.CommentUtil
import com.omeron.util.HtmlParser
import com.omeron.util.extension.toMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentMapper2 @Inject constructor(
    @DispatchersModule.DefaultDispatcher defaultDispatcher: CoroutineDispatcher
) : Mapper<Child, Comment>(defaultDispatcher) {

    private val htmlParser: HtmlParser = HtmlParser(defaultDispatcher)

    override suspend fun toEntity(from: Child): Comment {
        throw UnsupportedOperationException()
    }

    suspend fun dataToEntity(
        data: CommentData,
        parent: PostData? = null
    ): Comment.CommentEntity = withContext(defaultDispatcher) {
        with(data) {
            Comment.CommentEntity(
                totalAwards,
                linkId,
                dataToEntities(replies?.data?.children, parent),
                author,
                scoreString,
                awardings.sortedByDescending { it.count }.map { Award(it.count, it.getIcon()) },
                bodyHtml,
                htmlParser.separateHtmlBlocks(bodyHtml),
                editedMillis,
                isSubmitter,
                stickied,
                scoreHidden,
                permalink,
                id,
                created.toMillis(),
                controversiality,
                Flair.fromData(authorFlairRichText, flair),
                PosterType.fromDistinguished(distinguished),
                linkTitle ?: parent?.title,
                linkPermalink ?: parent?.permalink,
                linkAuthor ?: parent?.author,
                subreddit,
                CommentUtil.getCommentIndicator(depth),
                name,
                depth ?: 0
            )
        }
    }

    fun dataToEntity(data: MoreData): Comment.MoreEntity {
        with(data) {
            return Comment.MoreEntity(
                count,
                children,
                id,
                parentId,
                CommentUtil.getCommentIndicator(depth),
                name,
                depth ?: 0
            )
        }
    }

    suspend fun dataToEntity(
        data: Child,
        parent: PostData? = null
    ): Comment = withContext(defaultDispatcher) {
        when (data.kind) {
            ChildType.t1 -> dataToEntity((data as CommentChild).data, parent)
            ChildType.more -> dataToEntity((data as MoreChild).data)
            else -> throw IllegalStateException()
        }
    }

    suspend fun dataToEntities(
        data: List<Child>?,
        parent: PostData?
    ): MutableList<Comment> = withContext(defaultDispatcher) {
        data
            ?.filter {
                it.kind == ChildType.t1 || it.kind == ChildType.more
            }
            ?.map {
                dataToEntity(it, parent)
            } as MutableList<Comment>? ?: mutableListOf()
    }
}
