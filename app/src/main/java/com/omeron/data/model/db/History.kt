package com.omeron.data.model.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.PosterType
import com.omeron.data.model.Sorting

// Snapshot of the fields PostEntity persists (same set as the `post`/saved table) so the
// History tab can render full post cards via the existing PostViewHolder without a join.
@Entity(
    tableName = "history",
    primaryKeys = ["post_id", "profile_id"],
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class History(
    @ColumnInfo(name = "post_id")
    val postId: String,

    val subreddit: String,

    val title: String,

    val ratio: Int,

    @ColumnInfo(name = "total_awards")
    val totalAwards: Int,

    @ColumnInfo(name = "oc")
    val isOC: Boolean,

    val score: String,

    val type: PostType,

    val domain: String,

    @ColumnInfo(name = "self")
    val isSelf: Boolean,

    @ColumnInfo(name = "self_text_html")
    val selfTextHtml: String?,

    @ColumnInfo(name = "suggested_sorting")
    val suggestedSorting: Sorting,

    @ColumnInfo(name = "nsfw")
    val isOver18: Boolean,

    val preview: String?,

    @ColumnInfo(name = "spoiler")
    val isSpoiler: Boolean,

    @ColumnInfo(name = "archived")
    val isArchived: Boolean,

    @ColumnInfo(name = "locked")
    val isLocked: Boolean,

    @ColumnInfo(name = "poster_type")
    val posterType: PosterType,

    val author: String,

    @ColumnInfo(name = "comments_number")
    val commentsNumber: String,

    val permalink: String,

    @ColumnInfo(name = "stickied")
    val isStickied: Boolean,

    val url: String,

    val created: Long,

    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,

    @ColumnInfo(name = "media_url")
    val mediaUrl: String,

    @ColumnInfo(name = "time")
    val time: Long,

    @ColumnInfo(name = "profile_id", index = true)
    val profileId: Int
) {

    // Ignored columns on PostEntity (flairs, awards, gallery, ...) are not persisted here
    // either - same degraded fidelity the Saved-posts snapshot already accepts.
    fun toPostEntity(saved: Boolean): PostEntity = PostEntity(
        id = postId,
        subreddit = subreddit,
        title = title,
        ratio = ratio,
        totalAwards = totalAwards,
        isOC = isOC,
        score = score,
        type = type,
        domain = domain,
        isSelf = isSelf,
        selfTextHtml = selfTextHtml,
        suggestedSorting = suggestedSorting,
        isOver18 = isOver18,
        preview = preview,
        isSpoiler = isSpoiler,
        isArchived = isArchived,
        isLocked = isLocked,
        posterType = posterType,
        author = author,
        commentsNumber = commentsNumber,
        permalink = permalink,
        isStickied = isStickied,
        url = url,
        created = created,
        mediaType = mediaType,
        mediaUrl = mediaUrl,
        seen = true,
        saved = saved,
        time = time,
        profileId = profileId
    )

    companion object {
        fun fromPostEntity(post: PostEntity, time: Long): History = History(
            postId = post.id,
            subreddit = post.subreddit,
            title = post.title,
            ratio = post.ratio,
            totalAwards = post.totalAwards,
            isOC = post.isOC,
            score = post.score,
            type = post.type,
            domain = post.domain,
            isSelf = post.isSelf,
            selfTextHtml = post.selfTextHtml,
            suggestedSorting = post.suggestedSorting,
            isOver18 = post.isOver18,
            preview = post.preview,
            isSpoiler = post.isSpoiler,
            isArchived = post.isArchived,
            isLocked = post.isLocked,
            posterType = post.posterType,
            author = post.author,
            commentsNumber = post.commentsNumber,
            permalink = post.permalink,
            isStickied = post.isStickied,
            url = post.url,
            created = post.created,
            mediaType = post.mediaType,
            mediaUrl = post.mediaUrl,
            time = time,
            profileId = post.profileId
        )
    }
}
