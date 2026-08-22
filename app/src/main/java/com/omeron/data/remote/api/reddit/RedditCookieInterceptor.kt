package com.omeron.data.remote.api.reddit

import com.omeron.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Sends the reddit session cookie captured by [com.omeron.ui.login.RedditLoginActivity]
 * (plus over18=1) on every reddit request, and fails fast when reddit bounces the request
 * to its login wall instead of letting the scrapers parse an empty login page.
 */
class RedditCookieInterceptor(
    private val preferencesRepository: PreferencesRepository
) : Interceptor {

    class LoginRequiredException : IOException("Reddit login required")

    override fun intercept(chain: Interceptor.Chain): Response {
        // ponytail: blocking DataStore read per request; it's memory-cached after the first read.
        val session = runBlocking { preferencesRepository.getRedditCookies().first() }
        val cookie = if (session.isBlank()) OVER_18 else "$session; $OVER_18"

        val response = chain.proceed(
            chain.request().newBuilder().header("Cookie", cookie).build()
        )

        if (response.request.url.encodedPath.startsWith("/login")) {
            response.close()
            throw LoginRequiredException()
        }
        return response
    }

    companion object {
        private const val OVER_18 = "over18=1"
    }
}
