package com.omeron.data.remote.api.reddit.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** What the scraper appends to a video's address when the page has no signed manifest. */
const val UNSIGNED_DASH_SUFFIX = "/DASHPlaylist.mpd"

@JsonClass(generateAdapter = true)
data class RedditVideoPreview (
    @Json(name = "fallback_url")
    val fallbackUrl: String,

    @Json(name = "height")
    val height: Int,

    @Json(name = "width")
    val width: Int,

    @Json(name = "duration")
    val duration: Int,

    @Json(name = "is_gif")
    val isGif: Boolean
)