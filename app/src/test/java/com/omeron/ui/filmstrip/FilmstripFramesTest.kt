package com.omeron.ui.filmstrip

import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.PosterType
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.PostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmstripFramesTest {

    private val noInstantMedia: (PostEntity) -> List<GalleryMedia>? = { null }

    private fun post(
        id: String,
        type: PostType = PostType.IMAGE,
        mediaType: MediaType = MediaType.IMAGE,
        gallery: List<GalleryMedia> = emptyList()
    ) = PostEntity(
        id = id,
        subreddit = "r/test",
        title = "Title $id",
        ratio = 100,
        totalAwards = 0,
        isOC = false,
        score = "1",
        type = type,
        domain = "i.redd.it",
        isSelf = false,
        selfTextHtml = null,
        suggestedSorting = Sorting(Sort.BEST),
        isOver18 = false,
        preview = "https://preview/$id.jpg",
        isSpoiler = false,
        isArchived = false,
        isLocked = false,
        posterType = PosterType.REGULAR,
        author = "author",
        commentsNumber = "0",
        permalink = "/r/test/comments/$id",
        isStickied = false,
        url = "https://example.com/$id",
        created = 0L,
        mediaType = mediaType,
        mediaUrl = "https://media/$id",
        gallery = gallery
    )

    private fun image(name: String) = GalleryMedia(GalleryMedia.Type.IMAGE, "https://media/$name.jpg")

    @Test
    fun `single image post is one ready frame`() {
        val frames = buildFilmstripFrames(
            listOf(post("a")),
            emptyMap()
        ) { listOf(image("a")) }

        assertEquals(1, frames.size)
        assertEquals("a#0", frames[0].id)
        assertEquals(FrameStatus.READY, frames[0].status)
        assertEquals(1, frames[0].framesInPost)
        assertEquals(false, frames[0].isGalleryFrame)
    }

    @Test
    fun `video post is one ready video frame`() {
        val video = GalleryMedia(GalleryMedia.Type.VIDEO, "https://media/v.mp4")

        val frames = buildFilmstripFrames(
            listOf(post("v", type = PostType.VIDEO, mediaType = MediaType.REDDIT_VIDEO)),
            emptyMap()
        ) { listOf(video) }

        assertEquals(1, frames.size)
        assertTrue(frames[0].isVideo)
    }

    @Test
    fun `gallery on the entity becomes one frame per image`() {
        val gallery = listOf(image("1"), image("2"), image("3"))

        val frames = buildFilmstripFrames(
            listOf(post("g", mediaType = MediaType.REDDIT_GALLERY, gallery = gallery)),
            emptyMap(),
            noInstantMedia
        )

        assertEquals(listOf("g#0", "g#1", "g#2"), frames.map { it.id })
        assertEquals(listOf(0, 1, 2), frames.map { it.indexInPost })
        assertTrue(frames.all { it.framesInPost == 3 && it.status == FrameStatus.READY })
        assertEquals(gallery, frames.map { it.media })
    }

    @Test
    fun `frames follow post order across a gallery and its neighbours`() {
        val posts = listOf(
            post("a"),
            post("g", mediaType = MediaType.REDDIT_GALLERY, gallery = listOf(image("1"), image("2"))),
            post("b")
        )

        val frames = buildFilmstripFrames(posts, emptyMap()) { listOf(image(it.id)) }

        assertEquals(listOf("a#0", "g#0", "g#1", "b#0"), frames.map { it.id })
    }

    @Test
    fun `post that needs a lookup is a loading placeholder`() {
        val albumPost = post("album", mediaType = MediaType.IMGUR_ALBUM)

        val frames = buildFilmstripFrames(listOf(albumPost), emptyMap(), noInstantMedia)

        assertEquals(1, frames.size)
        assertEquals("album#0", frames[0].id)
        assertEquals(FrameStatus.LOADING, frames[0].status)
        assertNull(frames[0].media)
    }

    @Test
    fun `failed lookup is a failed placeholder`() {
        val albumPost = post("album", mediaType = MediaType.IMGUR_ALBUM)

        val frames = buildFilmstripFrames(
            listOf(albumPost),
            mapOf("album" to PostMediaState.Failed),
            noInstantMedia
        )

        assertEquals(FrameStatus.FAILED, frames.single().status)
    }

    @Test
    fun `resolved gallery keeps the placeholder id and inserts the extra frames after it`() {
        val albumPost = post("album", mediaType = MediaType.IMGUR_ALBUM)
        val following = post("next")
        val posts = listOf(albumPost, following)
        val instantMedia: (PostEntity) -> List<GalleryMedia>? = { candidate ->
            if (candidate.id == "next") listOf(image("next")) else null
        }

        val before = buildFilmstripFrames(
            posts,
            mapOf("album" to PostMediaState.Loading),
            instantMedia
        )
        val after = buildFilmstripFrames(
            posts,
            mapOf("album" to PostMediaState.Resolved(listOf(image("1"), image("2"), image("3")))),
            instantMedia
        )

        assertEquals(listOf("album#0", "next#0"), before.map { it.id })
        assertEquals(listOf("album#0", "album#1", "album#2", "next#0"), after.map { it.id })
        assertEquals(FrameStatus.READY, after[0].status)
        assertEquals(3, after[0].framesInPost)
    }

    @Test
    fun `resolved lookup with no media is a failed placeholder`() {
        val albumPost = post("album", mediaType = MediaType.REDDIT_GALLERY)

        val frames = buildFilmstripFrames(
            listOf(albumPost),
            mapOf("album" to PostMediaState.Resolved(emptyList())),
            noInstantMedia
        )

        assertEquals(FrameStatus.FAILED, frames.single().status)
    }
}
