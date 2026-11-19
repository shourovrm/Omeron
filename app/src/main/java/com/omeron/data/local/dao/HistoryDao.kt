package com.omeron.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.omeron.data.model.db.History
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao : BaseDao<History> {

    @Query("DELETE FROM history WHERE profile_id = :profileId")
    abstract suspend fun deleteFromProfile(profileId: Int)

    @Query(
        "DELETE FROM history WHERE profile_id = :profileId AND post_id NOT IN " +
            "(SELECT post_id FROM history WHERE profile_id = :profileId ORDER BY time DESC LIMIT :keep)"
    )
    abstract suspend fun deleteOlderThanNewest(profileId: Int, keep: Int)

    @Query("DELETE FROM history WHERE post_id = :postId AND profile_id = :profileId")
    abstract suspend fun deleteFromIdAndProfile(postId: String, profileId: Int)

    // The rows carry post text and can span several cursor windows; the transaction keeps a
    // write between two windows from breaking the read.
    @Transaction
    @Query("SELECT * FROM history WHERE profile_id = :profileId ORDER BY time DESC")
    abstract fun getHistoryFromProfile(profileId: Int): Flow<List<History>>

    @Query("SELECT post_id FROM history WHERE profile_id = :profileId")
    abstract fun getHistoryIdsFromProfile(profileId: Int): Flow<List<String>>
}
