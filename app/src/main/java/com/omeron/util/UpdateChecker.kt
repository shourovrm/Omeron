package com.omeron.util

import com.omeron.BuildConfig
import com.omeron.di.DispatchersModule.IoDispatcher
import com.omeron.di.NetworkModule.GenericOkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub releases for a newer version than the running build. All endpoints
 * public, no auth.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @GenericOkHttp private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    data class Update(val version: String, val changelog: String)

    /** Swallows network/parse failures (returns null) - used for the silent launch-time check. */
    suspend fun check(): Update? = runCatching { checkOrThrow() }.getOrNull()

    /** Same as [check] but throws on failure instead of swallowing it, so callers that need to
     * tell "up to date" (null) apart from "check failed" (exception) can do so. */
    suspend fun checkOrThrow(): Update? = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Update check failed: ${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val version = json.getString("tag_name").removePrefix("v")
            if (!isNewer(version, BuildConfig.VERSION_NAME)) return@use null

            Update(version, json.optString("body"))
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/shourovrm/Omeron/releases/latest"
    }
}
