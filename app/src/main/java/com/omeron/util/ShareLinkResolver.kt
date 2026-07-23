package com.omeron.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resolves reddit.com/r/{sub}/s/{id} (and /u/{user}/s/{id}) share links: server-side redirects
 * to the real permalink, e.g. /r/{sub}/comments/{id}/{slug}/?share_id=...&utm_...
 */
object ShareLinkResolver {

    private val SHARE_PATH_REGEX = Regex("^/(r|u)/[^/]+/s/[A-Za-z0-9]+/?$")

    private const val MAX_HOPS = 3
    private const val TIMEOUT_SECONDS = 10L

    // ponytail: own client instead of the shared @RedditOkHttp one - its JsonInterceptor /
    // RawJsonInterceptor rewrite every request to <url>/.json?raw_json=1, which would break a
    // share-link redirect fetch. Still reuses LinkUtil.USER_AGENT to dodge the same 403s.
    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", LinkUtil.USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    fun isShareLink(uri: Uri): Boolean = isSharePath(uri.path)

    fun isSharePath(path: String?): Boolean = path != null && SHARE_PATH_REGEX.matches(path)

    /**
     * Follows the redirect chain (GET, body discarded) and strips tracking query params from
     * the final permalink. Returns null on any failure (no Location header, IOException,
     * non-3xx response, or more than [MAX_HOPS] hops) so the caller can fall back.
     */
    suspend fun resolve(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        var url = uri.toString().toHttpUrlOrNull() ?: return@withContext null

        repeat(MAX_HOPS) {
            val response = try {
                client.newCall(Request.Builder().url(url).get().build()).execute()
            } catch (e: IOException) {
                return@withContext null
            }

            val location = response.use { resp ->
                resp.header("Location").takeIf { resp.code in 300..399 }
            } ?: return@withContext null

            url = url.resolve(location) ?: return@withContext null

            if (!isSharePath(url.encodedPath)) {
                return@withContext Uri.parse(stripTracking(url).toString())
            }
        }

        null
    }

    private fun stripTracking(url: HttpUrl): HttpUrl = url.newBuilder().query(null).build()
}
