package com.omeron.ui.filmstrip

import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.db.PostEntity

enum class FrameStatus {
    // The frame has media to show.
    READY,

    // The post's media is being looked up; the frame shows the feed preview meanwhile.
    LOADING,

    // The lookup failed or found nothing; the frame offers a retry.
    FAILED
}

/** Where a frame's media comes from. All frames of one source sit next to each other. */
sealed interface FrameSource {

    /** Used to tell sources apart, and as the prefix of the ids of the source's frames. */
    val key: String

    /** A post of a feed, shown with its title, score and comments. */
    data class OfPost(val post: PostEntity) : FrameSource {
        override val key: String
            get() = post.id
    }

    /** A bare media link with no post behind it, such as a link inside a comment. */
    data class OfLink(val url: String) : FrameSource {
        override val key: String
            get() = LINK_KEY

        companion object {
            const val LINK_KEY = "link"
        }
    }
}

/** One swipeable page of the viewer: a whole post or link, or a single image of a gallery. */
data class FilmstripFrame(
    val id: String,
    val source: FrameSource,
    val indexInSource: Int,
    val framesInSource: Int,
    val media: GalleryMedia?,
    val status: FrameStatus
) {
    /** Null when the frame belongs to a bare link. */
    val post: PostEntity?
        get() = (source as? FrameSource.OfPost)?.post

    val isGalleryFrame: Boolean
        get() = framesInSource > 1

    val isVideo: Boolean
        get() = media?.type == GalleryMedia.Type.VIDEO
}

/** Outcome of the network lookup for a post whose media is not on the feed entity. */
sealed interface PostMediaState {
    object Loading : PostMediaState

    object Failed : PostMediaState

    data class Resolved(val media: List<GalleryMedia>) : PostMediaState
}

/**
 * Swaps in the full post for every feed post that was fetched in full (search results, which the
 * feed only lists as pointers). Keeps order and length, so the pager never shifts.
 */
fun withHydratedPosts(
    posts: List<PostEntity>,
    hydratedPosts: Map<String, PostEntity>
): List<PostEntity> {
    return posts.map { post -> hydratedPosts[post.id] ?: post }
}

/**
 * Flattens posts into the pager's frames. A gallery contributes one frame per image, so swiping
 * moves through a gallery and on to the next post without special cases.
 *
 * A post's media comes from, in order: a finished network lookup, the gallery already on the
 * entity, or a link that needs no lookup ([instantMedia]). Anything else becomes one placeholder
 * frame that keeps the id "<post id>#0", so when the lookup finishes the placeholder is replaced
 * in place and any extra gallery frames are inserted after it.
 */
fun buildFilmstripFrames(
    posts: List<PostEntity>,
    mediaStates: Map<String, PostMediaState>,
    instantMedia: (PostEntity) -> List<GalleryMedia>?
): List<FilmstripFrame> {
    return posts.flatMap { post ->
        val mediaState = mediaStates[post.id]
        val media = when {
            mediaState is PostMediaState.Resolved -> mediaState.media
            post.gallery.isNotEmpty() -> post.gallery
            else -> instantMedia(post)
        }
        framesOfSource(FrameSource.OfPost(post), media, mediaState)
    }
}

/**
 * Frames of a bare media link. The media comes from a finished lookup, or else from
 * [instantMedia], the link's media when it needs no lookup. Without either there is one
 * placeholder frame, "link#0", that the lookup later replaces in place.
 */
fun buildLinkFrames(
    url: String,
    mediaState: PostMediaState?,
    instantMedia: List<GalleryMedia>?
): List<FilmstripFrame> {
    val media = (mediaState as? PostMediaState.Resolved)?.media ?: instantMedia
    return framesOfSource(FrameSource.OfLink(url), media, mediaState)
}

private fun framesOfSource(
    source: FrameSource,
    media: List<GalleryMedia>?,
    mediaState: PostMediaState?
): List<FilmstripFrame> {
    if (media.isNullOrEmpty()) {
        val status = if (mediaState is PostMediaState.Failed || mediaState is PostMediaState.Resolved) {
            FrameStatus.FAILED
        } else {
            FrameStatus.LOADING
        }
        return listOf(FilmstripFrame(frameId(source, 0), source, 0, 1, null, status))
    }

    return media.mapIndexed { index, galleryMedia ->
        FilmstripFrame(
            frameId(source, index),
            source,
            index,
            media.size,
            galleryMedia,
            FrameStatus.READY
        )
    }
}

/** Which neighbouring post a vertical swipe moves to. */
enum class PostSwipeDirection { PREVIOUS, NEXT }

/**
 * Index of the first frame of the post a vertical swipe lands on, or null when there is no such
 * post (first post going back, last loaded post going forward). The rest of a gallery is skipped
 * going forward, and going back always lands on the previous post, never on the start of the
 * current one. A post's frames are contiguous, so only the post ids need comparing.
 */
fun postSwipeTargetIndex(
    frames: List<FilmstripFrame>,
    currentIndex: Int,
    direction: PostSwipeDirection
): Int? {
    val currentPostId = frames.getOrNull(currentIndex)?.source?.key ?: return null

    return when (direction) {
        PostSwipeDirection.NEXT -> {
            (currentIndex + 1 until frames.size).firstOrNull { frames[it].source.key != currentPostId }
        }
        PostSwipeDirection.PREVIOUS -> {
            val currentPostStart = (currentIndex downTo 0)
                .takeWhile { frames[it].source.key == currentPostId }
                .last()
            val previousPostEnd = currentPostStart - 1
            if (previousPostEnd < 0) return null
            val previousPostId = frames[previousPostEnd].source.key
            (previousPostEnd downTo 0)
                .takeWhile { frames[it].source.key == previousPostId }
                .last()
        }
    }
}

private fun frameId(source: FrameSource, indexInSource: Int) = "${source.key}#$indexInSource"
