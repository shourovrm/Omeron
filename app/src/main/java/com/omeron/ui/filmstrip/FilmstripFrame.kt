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

/** One swipeable page of the viewer: a whole post, or a single image of a gallery post. */
data class FilmstripFrame(
    val id: String,
    val post: PostEntity,
    val indexInPost: Int,
    val framesInPost: Int,
    val media: GalleryMedia?,
    val status: FrameStatus
) {
    val isGalleryFrame: Boolean
        get() = framesInPost > 1

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

        if (media.isNullOrEmpty()) {
            val status = if (mediaState is PostMediaState.Failed || mediaState is PostMediaState.Resolved) {
                FrameStatus.FAILED
            } else {
                FrameStatus.LOADING
            }
            listOf(
                FilmstripFrame(frameId(post, 0), post, 0, 1, null, status)
            )
        } else {
            media.mapIndexed { index, galleryMedia ->
                FilmstripFrame(
                    frameId(post, index),
                    post,
                    index,
                    media.size,
                    galleryMedia,
                    FrameStatus.READY
                )
            }
        }
    }
}

private fun frameId(post: PostEntity, indexInPost: Int) = "${post.id}#$indexInPost"
