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
        gallery: List<GalleryMedia> = emptyList(),
        url: String = "https://example.com/$id",
        preview: String? = "https://preview/$id.jpg"
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
        preview = preview,
        isSpoiler = false,
        isArchived = false,
        isLocked = false,
        posterType = PosterType.REGULAR,
        author = "author",
        commentsNumber = "0",
        permalink = "/r/test/comments/$id",
        isStickied = false,
        url = url,
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

    // A search result as the scraper maps it: no link, only a thumbnail.
    private fun searchResult(id: String) = post(
        id,
        type = PostType.LINK,
        mediaType = MediaType.LINK,
        url = "",
        preview = "https://thumbs/$id.jpg"
    )

    private fun fullPost(id: String, mediaType: MediaType = MediaType.IMAGE) = post(
        id,
        mediaType = mediaType,
        preview = "https://preview/full-$id.jpg"
    )

    @Test
    fun `search result is a loading placeholder that shows its thumbnail`() {
        val frames = buildFilmstripFrames(listOf(searchResult("s")), emptyMap(), noInstantMedia)

        assertEquals("s#0", frames.single().id)
        assertEquals(FrameStatus.LOADING, frames.single().status)
        assertEquals("https://thumbs/s.jpg", frames.single().post.preview)
    }

    @Test
    fun `hydrated posts replace search results in place and keep the order`() {
        val posts = listOf(searchResult("a"), searchResult("b"), searchResult("c"))
        val hydrated = mapOf("b" to fullPost("b"))

        val merged = withHydratedPosts(posts, hydrated)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals(posts[0], merged[0])
        assertEquals(hydrated.getValue("b"), merged[1])
        assertEquals(posts[2], merged[2])
    }

    @Test
    fun `hydrated gallery gets its frames after the placeholder id`() {
        val hydratedGallery = post(
            "s",
            mediaType = MediaType.REDDIT_GALLERY,
            gallery = listOf(image("1"), image("2"))
        )

        val frames = buildFilmstripFrames(
            withHydratedPosts(listOf(searchResult("s"), searchResult("t")), mapOf("s" to hydratedGallery)),
            mapOf("s" to PostMediaState.Loading),
            noInstantMedia
        )

        assertEquals(listOf("s#0", "s#1", "t#0"), frames.map { it.id })
        assertEquals(FrameStatus.READY, frames[0].status)
        assertEquals(FrameStatus.LOADING, frames[2].status)
        assertEquals(hydratedGallery, frames[0].post)
    }

    @Test
    fun `hydrated link post resolved to its thumbnail is one ready image frame`() {
        val thumbnailFrame = GalleryMedia.singleton(GalleryMedia.Type.IMAGE, "https://thumbs/s.jpg")
        val hydratedLink = fullPost("s", mediaType = MediaType.LINK)

        val frames = buildFilmstripFrames(
            withHydratedPosts(listOf(searchResult("s")), mapOf("s" to hydratedLink)),
            mapOf("s" to PostMediaState.Resolved(thumbnailFrame)),
            noInstantMedia
        )

        assertEquals(1, frames.size)
        assertEquals(FrameStatus.READY, frames.single().status)
        assertEquals("https://thumbs/s.jpg", frames.single().media?.url)
    }

    @Test
    fun `failed hydration keeps the search result as a failed placeholder`() {
        val frames = buildFilmstripFrames(
            listOf(searchResult("s")),
            mapOf("s" to PostMediaState.Failed),
            noInstantMedia
        )

        assertEquals(FrameStatus.FAILED, frames.single().status)
        assertEquals(true, frames.single().post.needsHydration)
    }

    private fun framesFor(vararg postsWithImageCounts: Pair<String, Int>): List<FilmstripFrame> {
        val imageCounts = postsWithImageCounts.toMap()
        return buildFilmstripFrames(
            postsWithImageCounts.map { (id, _) -> post(id) },
            emptyMap()
        ) { post -> (0 until imageCounts.getValue(post.id)).map { image("${post.id}$it") } }
    }

    @Test
    fun `next post from a single frame post is the following frame`() {
        val frames = framesFor("a" to 1, "b" to 1, "c" to 1)

        assertEquals(1, postSwipeTargetIndex(frames, 0, PostSwipeDirection.NEXT))
        assertEquals(2, postSwipeTargetIndex(frames, 1, PostSwipeDirection.NEXT))
    }

    @Test
    fun `next post from the middle of a gallery skips its remaining images`() {
        val frames = framesFor("a" to 1, "g" to 4, "c" to 2)

        // g occupies indices 1..4; from g#1 (index 2) the next post starts at index 5.
        assertEquals(5, postSwipeTargetIndex(frames, 2, PostSwipeDirection.NEXT))
    }

    @Test
    fun `previous post from the middle of a gallery goes to the previous post not the gallery start`() {
        val frames = framesFor("a" to 1, "g" to 4, "c" to 1)

        assertEquals(0, postSwipeTargetIndex(frames, 3, PostSwipeDirection.PREVIOUS))
    }

    @Test
    fun `previous post lands on the first frame of a previous gallery`() {
        val frames = framesFor("g" to 3, "c" to 2)

        // c#1 is index 4; g starts at index 0.
        assertEquals(0, postSwipeTargetIndex(frames, 4, PostSwipeDirection.PREVIOUS))
        assertEquals(0, postSwipeTargetIndex(frames, 3, PostSwipeDirection.PREVIOUS))
    }

    @Test
    fun `previous post from the first frame of a post is the previous post`() {
        val frames = framesFor("a" to 2, "b" to 1, "c" to 3)

        assertEquals(2, postSwipeTargetIndex(frames, 3, PostSwipeDirection.PREVIOUS))
        assertEquals(0, postSwipeTargetIndex(frames, 2, PostSwipeDirection.PREVIOUS))
    }

    @Test
    fun `no previous post from any frame of the first post`() {
        val frames = framesFor("g" to 3, "b" to 1)

        assertNull(postSwipeTargetIndex(frames, 0, PostSwipeDirection.PREVIOUS))
        assertNull(postSwipeTargetIndex(frames, 2, PostSwipeDirection.PREVIOUS))
    }

    @Test
    fun `no next post from any frame of the last loaded post`() {
        val frames = framesFor("a" to 1, "g" to 3)

        assertNull(postSwipeTargetIndex(frames, 1, PostSwipeDirection.NEXT))
        assertNull(postSwipeTargetIndex(frames, 3, PostSwipeDirection.NEXT))
    }

    @Test
    fun `placeholder frames count as posts to move between`() {
        val frames = buildFilmstripFrames(
            listOf(post("a"), post("loading"), post("failed"), post("d")),
            mapOf("failed" to PostMediaState.Failed)
        ) { post -> if (post.id == "a" || post.id == "d") listOf(image(post.id)) else null }

        assertEquals(FrameStatus.LOADING, frames[1].status)
        assertEquals(FrameStatus.FAILED, frames[2].status)
        assertEquals(1, postSwipeTargetIndex(frames, 0, PostSwipeDirection.NEXT))
        assertEquals(2, postSwipeTargetIndex(frames, 1, PostSwipeDirection.NEXT))
        assertEquals(1, postSwipeTargetIndex(frames, 2, PostSwipeDirection.PREVIOUS))
        assertEquals(2, postSwipeTargetIndex(frames, 3, PostSwipeDirection.PREVIOUS))
    }

    @Test
    fun `an index outside the frames has no target`() {
        val frames = framesFor("a" to 1)

        assertNull(postSwipeTargetIndex(frames, -1, PostSwipeDirection.NEXT))
        assertNull(postSwipeTargetIndex(emptyList(), 0, PostSwipeDirection.PREVIOUS))
    }
}
