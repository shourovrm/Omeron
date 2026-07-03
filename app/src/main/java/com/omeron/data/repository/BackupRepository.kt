package com.omeron.data.repository

import android.net.Uri
import com.omeron.data.local.backup.BackupManager
import com.omeron.data.local.backup.RedditBackupManager
import com.omeron.data.local.backup.OmeronBackupManager
import com.omeron.data.model.Resource
import com.omeron.data.model.backup.BackupType
import com.omeron.data.model.backup.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val omeronBackupManager: OmeronBackupManager,
    private val redditBackupManager: RedditBackupManager
) {

    fun importProfiles(uri: Uri, type: BackupType): Flow<Resource<List<Profile>>> = flow {
        emit(Resource.Loading<List<Profile>>())

        val backupManager = getBackupManager(type)
        backupManager
            .import(uri)
            .onSuccess { profiles ->
                emit(Resource.Success(profiles))
            }
            .onFailure { e ->
                emit(Resource.Error<List<Profile>>(throwable = e))
            }
    }

    fun exportProfiles(uri: Uri, type: BackupType): Flow<Resource<List<Profile>>> = flow {
        emit(Resource.Loading<List<Profile>>())

        val backupManager = getBackupManager(type)
        backupManager
            .export(uri)
            .onSuccess { profiles ->
                emit(Resource.Success(profiles))
            }
            .onFailure { e ->
                emit(Resource.Error<List<Profile>>(throwable = e))
            }
    }

    private fun getBackupManager(type: BackupType): BackupManager {
        return when (type) {
            BackupType.OMERON -> omeronBackupManager
            BackupType.REDDIT -> redditBackupManager
        }
    }
}
