package com.omeron.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.omeron.data.model.db.PostEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PostDao : BaseDao<PostEntity> {

    @Query("DELETE FROM post WHERE id = :id AND profile_id = :profileId")
    abstract suspend fun deleteFromIdAndProfile(id: String, profileId: Int)

    @Query("DELETE FROM post WHERE profile_id = :profileId")
    abstract suspend fun deleteFromProfile(profileId: Int)

    @Query("SELECT id FROM post WHERE profile_id = :profileId")
    abstract fun getSavedPostIdsFromProfile(profileId: Int): Flow<List<String>>

    // The rows carry post text and can span several cursor windows; the transaction keeps a
    // write between two windows from breaking the read.
    @Transaction
    @Query("SELECT * FROM post WHERE profile_id = :profileId")
    abstract fun getSavedPostsFromProfile(profileId: Int): Flow<List<PostEntity>>
}
