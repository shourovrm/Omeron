package com.omeron.data.local.backup

import android.content.Context
import android.net.Uri
import com.omeron.data.local.RedditDatabase
import com.omeron.data.local.mapper.BackupCommentMapper
import com.omeron.data.local.mapper.BackupPostMapper
import com.omeron.data.local.mapper.ProfileMapper
import com.omeron.data.local.mapper.SubscriptionMapper
import com.omeron.data.model.backup.Profile
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.di.DispatchersModule.IoDispatcher
import com.omeron.di.NetworkModule.BasicMoshi
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import okio.use
import javax.inject.Inject
import javax.inject.Singleton

typealias ProfileDb = com.omeron.data.model.db.Profile

@Singleton
class OmeronBackupManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    redditDatabase: RedditDatabase,
    profileMapper: ProfileMapper,
    subscriptionMapper: SubscriptionMapper,
    backupPostMapper: BackupPostMapper,
    backupCommentMapper: BackupCommentMapper,
    @BasicMoshi private val moshi: Moshi,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BackupManager(
    redditDatabase,
    profileMapper,
    subscriptionMapper,
    backupPostMapper,
    backupCommentMapper,
    defaultDispatcher
) {

    override suspend fun import(uri: Uri): Result<List<Profile>> {
        val adapter = moshi.adapter<List<Profile>>(
            Types.newParameterizedType(List::class.java, Profile::class.java)
        )

        return runCatching {
            withContext(ioDispatcher) {
                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.source().buffer().use { bufferedSource ->
                        adapter.fromJson(bufferedSource)
                    }
                } ?: emptyList()
            }
        }.onSuccess { profiles ->
            insertProfiles(profiles)
        }
    }

    override suspend fun export(uri: Uri): Result<List<Profile>> {
        val profiles = getProfiles()

        val adapter = moshi.adapter<List<Profile>>(
            Types.newParameterizedType(List::class.java, Profile::class.java)
        ).indent("  ")

        return runCatching {
            withContext(ioDispatcher) {
                appContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.sink().buffer().use { bufferedSink ->
                        adapter.toJson(bufferedSink, profiles)
                    }
                }
            }
            profiles
        }
    }
}
