package com.omeron.data.remote.api.reddit.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class MediaMetadata(
    val items: List<GalleryItem>
)
