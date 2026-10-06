package com.omeron.data.model.db

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import com.omeron.R
import com.omeron.data.model.Award
import com.omeron.data.model.Flair
import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.PosterType
import com.omeron.data.model.RedditText
import com.omeron.data.model.Sorting
import com.omeron.data.model.preferences.ContentPreferences
import com.omeron.data.remote.api.reddit.model.Crosspost
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "post",
    primaryKeys = ["id", "profile_id"],
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PostEntity @JvmOverloads constructor(
    @ColumnInfo(name = "id")
    val id: String,

    val subreddit: String,

    val title: String,

    val ratio: Int,

    @ColumnInfo(name = "total_awards")
    val totalAwards: Int,

    @ColumnInfo(name = "oc")
    val isOC: Boolean,

    @Ignore
    val flair: Flair = Flair(), // TODO: Handle flairs

    @Ignore
    val authorFlair: Flair = Flair(), // TODO: Handle flairs

    @Ignore
    var hasFlairs: Boolean = false,

    val score: String,

    val type: PostType,

    val domain: String,

    @ColumnInfo(name = "self")
    val isSelf: Boolean,

    @Ignore
    val crosspost: PostEntity? = null,

    @ColumnInfo(name = "self_text_html")
    val selfTextHtml: String?,

    @ColumnInfo(name = "suggested_sorting")
    val suggestedSorting: Sorting,

    @Ignore
    var selfRedditText: RedditText = RedditText(),

    @ColumnInfo(name = "nsfw")
    val isOver18: Boolean,

    val preview: String?,

    @Ignore
    var previewText: CharSequence? = null,

    @Ignore
    val awards: List<Award> = listOf(),

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

    @Ignore
    val gallery: List<GalleryMedia> = listOf(),

    @Ignore
    var seen: Boolean = true,

    @Ignore
    var saved: Boolean = true,

    @ColumnInfo(name = "time")
    var time: Long = -1,

    @ColumnInfo(name = "profile_id", index = true)
    var profileId: Int = -1,

    @Ignore
    var crosspostScrap: Crosspost? = null
) : Parcelable {

    val textColor: Int
        get() = if (seen) R.color.text_color_post_seen else R.color.text_color

    // Scraped subreddit names arrive as "r/name"; avatars and navigation want the bare name.
    val subredditName: String
        get() = subreddit.removePrefix("r/")

    // Regular authors read as secondary text so the subreddit stays the header's focal point;
    // admins and moderators keep their role colour.
    val authorColor: Int
        get() = if (posterType == PosterType.REGULAR) R.color.text_color_secondary else posterType.color

    val isGallery: Boolean
        get() = mediaType == MediaType.REDDIT_GALLERY ||
            mediaType == MediaType.IMGUR_ALBUM ||
            mediaType == MediaType.IMGUR_GALLERY

    // A post belongs in the media-only grid when it is an image, gif, video or gallery AND has a
    // preview URL to draw as the tile. Link posts (articles, polls) are left out even when the
    // site supplies a thumbnail, because that thumbnail is not the post's media. Redgifs,
    // Streamable and Gfycat posts are typed VIDEO, imgur albums and galleries are typed IMAGE.
    fun hasFilmstripMedia(): Boolean {
        val isMediaType = type == PostType.IMAGE || type == PostType.VIDEO
        return isMediaType && !preview.isNullOrBlank()
    }

    // Search results are only pointers: no media type or link, just the thumbnail the result card
    // shows (which the mapper stores as the preview). So any non-self result with a real thumbnail
    // image may be media; the viewer finds out for sure once it fetches the full post.
    fun isFilmstripSearchCandidate(): Boolean {
        if (isSelf) return false
        val thumbnail = preview?.trim().orEmpty()
        val isPlaceholderKeyword = thumbnail.removePrefix("https:").lowercase() in THUMBNAIL_PLACEHOLDERS
        return thumbnail.startsWith("http") && !isPlaceholderKeyword
    }

    // A search result carries no link at all (the scraper leaves url empty), so a blank url
    // means the full post still has to be fetched before its media can be found.
    val needsHydration: Boolean
        get() = url.isBlank()

    fun shouldShowPreview(contentPreferences: ContentPreferences): Boolean {
        return (contentPreferences.showNsfwPreview || !isOver18) &&
                (contentPreferences.showSpoilerPreview || !isSpoiler)
    }

    private companion object {
        // Values Reddit puts in the thumbnail field instead of an image URL.
        val THUMBNAIL_PLACEHOLDERS = setOf("self", "default", "nsfw", "spoiler", "image")
    }
}
