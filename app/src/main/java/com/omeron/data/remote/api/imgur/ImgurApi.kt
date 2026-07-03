package com.omeron.data.remote.api.imgur

import com.omeron.data.remote.api.imgur.model.Album
import retrofit2.http.GET
import retrofit2.http.Path

interface ImgurApi {

    @GET("/ajaxalbums/getimages/{albumId}")
    suspend fun getAlbum(@Path("albumId") albumId: String): Album

    companion object {
        const val BASE_URL = "https://imgur.com/"
    }
}
