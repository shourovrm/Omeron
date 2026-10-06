package com.omeron.ui.mediaviewer

import com.omeron.data.local.mapper.PostMapper2
import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.GalleryMedia.Type
import com.omeron.data.model.MediaType
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.repository.GfycatRepository
import com.omeron.data.repository.ImgurRepository
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.RedgifsRepository
import com.omeron.data.repository.StreamableRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.util.LinkUtil
import com.omeron.util.LinkUtil.https
import com.omeron.util.PostUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Turns a post's media link into the list of images and videos to show. Shared by the single
 * media viewer and the Filmstrip viewer so both resolve every host the same way.
 */
class MediaResolver @Inject constructor(
    private val imgurRepository: ImgurRepository,
    private val streamableRepository: StreamableRepository,
    private val gfycatRepository: GfycatRepository,
    private val redgifsRepository: RedgifsRepository,
    private val postListRepository: PostListRepository,
    private val postMapper: PostMapper2,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    class UnsupportedMediaException(mediaType: MediaType) :
        Exception("No media can be shown for $mediaType")

    /**
     * Media that can be built from the link alone. Null means the type needs a network request
     * (see [resolve]) or is not a media type at all.
     */
    fun resolveWithoutNetwork(link: String, mediaType: MediaType): List<GalleryMedia>? {
        val httpsLink = link.https
        return when (mediaType) {
            MediaType.IMGUR_IMAGE, MediaType.IMAGE -> {
                GalleryMedia.singleton(Type.IMAGE, httpsLink)
            }
            MediaType.IMGUR_LINK -> {
                val id = LinkUtil.getImageIdFromImgurLink(httpsLink)
                GalleryMedia.singleton(Type.IMAGE, LinkUtil.getUrlFromImgurId(id))
            }
            MediaType.IMGUR_GIF -> {
                GalleryMedia.singleton(Type.VIDEO, LinkUtil.getImgurVideo(httpsLink))
            }
            MediaType.REDDIT_GIF, MediaType.IMGUR_VIDEO, MediaType.VIDEO -> {
                GalleryMedia.singleton(Type.VIDEO, httpsLink)
            }
            MediaType.REDDIT_VIDEO -> {
                GalleryMedia.singleton(
                    Type.VIDEO,
                    httpsLink,
                    LinkUtil.getRedditSoundTrackOrNull(httpsLink)
                )
            }
            else -> null
        }
    }

    /**
     * Resolves any supported media type, making the network request when one is needed.
     *
     * @throws UnsupportedMediaException when the type has no viewable media
     */
    suspend fun resolve(link: String, mediaType: MediaType): List<GalleryMedia> {
        resolveWithoutNetwork(link, mediaType)?.let { return it }

        val httpsLink = link.https
        return when (mediaType) {
            MediaType.GFYCAT -> {
                val gif = gfycatRepository.getGfycatGif(LinkUtil.getGfycatId(httpsLink)).first()
                GalleryMedia.singleton(Type.VIDEO, gif.gfyItem.contentUrls.mp4.url)
            }
            MediaType.REDGIFS -> {
                val gif = redgifsRepository.getRedgifsGif(LinkUtil.getGfycatId(httpsLink)).first()
                GalleryMedia.singleton(Type.VIDEO, gif.gif.urls.hd)
            }
            MediaType.STREAMABLE -> {
                val shortcode = LinkUtil.getStreamableShortcode(httpsLink)
                val video = streamableRepository.getVideo(shortcode).first()
                GalleryMedia.singleton(Type.VIDEO, video.files.mp4.url)
            }
            MediaType.IMGUR_ALBUM, MediaType.IMGUR_GALLERY -> resolveImgurAlbum(httpsLink)
            MediaType.REDDIT_GALLERY -> resolveRedditGallery(httpsLink)
            else -> throw UnsupportedMediaException(mediaType)
        }
    }

    private suspend fun resolveImgurAlbum(link: String): List<GalleryMedia> {
        val albumId = LinkUtil.getAlbumIdFromImgurLink(link)
        val album = imgurRepository.getAlbum(albumId).first()

        val images = withContext(defaultDispatcher) {
            album.data.images.map { image ->
                GalleryMedia(
                    if (image.preferVideo) Type.VIDEO else Type.IMAGE,
                    LinkUtil.getUrlFromImgurImage(image),
                    description = image.description
                )
            }
        }

        // Some Imgur galleries are empty and actually point to a single image
        return images.ifEmpty {
            GalleryMedia.singleton(Type.IMAGE, LinkUtil.getUrlFromImgurId(albumId))
        }
    }

    private suspend fun resolveRedditGallery(link: String): List<GalleryMedia> {
        val permalink = LinkUtil.getPermalinkFromMediaUrl(link)
        val listings = postListRepository.getPost(permalink, Sorting(Sort.BEST)).first()
        return postMapper.dataToEntity(PostUtil.getPostData(listings)).gallery
    }
}
