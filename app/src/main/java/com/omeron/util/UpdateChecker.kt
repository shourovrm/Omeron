package com.omeron.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.omeron.BuildConfig
import com.omeron.di.DispatchersModule.IoDispatcher
import com.omeron.di.NetworkModule.GenericOkHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-update against GitHub releases: check latest tag, download the APK asset,
 * hand it to the system package installer. All endpoints public, no auth.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    @GenericOkHttp private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    data class Update(val version: String, val changelog: String, val apkUrl: String)

    suspend fun check(): Update? = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val json = JSONObject(response.body?.string() ?: return@use null)
                val version = json.getString("tag_name").removePrefix("v")
                if (!isNewer(version, BuildConfig.VERSION_NAME)) return@use null

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        return@use Update(
                            version,
                            json.optString("body"),
                            asset.getString("browser_download_url")
                        )
                    }
                }
                null
            }
        }.getOrNull()
    }

    suspend fun download(update: Update): File? = withContext(ioDispatcher) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "omeron-${update.version}.apk")

            val request = Request.Builder().url(update.apkUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val body = response.body ?: return@use null
                body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file
            }
        }.getOrNull()
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
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
