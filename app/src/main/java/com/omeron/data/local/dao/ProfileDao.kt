package com.omeron.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.omeron.data.model.db.Profile
import com.omeron.data.model.db.ProfileCounts
import com.omeron.data.model.db.ProfileWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfileDao : BaseDao<Profile> {
    @Query("SELECT * FROM profile")
    abstract fun getAllProfiles(): Flow<List<Profile>>

    @Transaction
    @Query("SELECT * FROM profile")
    abstract suspend fun getProfilesWithDetails(): List<ProfileWithDetails>

    @Query("SELECT * FROM profile WHERE id = :id")
    abstract suspend fun getProfileFromId(id: Int): Profile?

    @Query("SELECT * FROM profile LIMIT 1")
    abstract suspend fun getFirstProfile(): Profile

    @Query(
        """
        SELECT profile.id AS profile_id,
            (SELECT COUNT(*) FROM subscription WHERE subscription.profile_id = profile.id)
                AS community_count,
            (SELECT COUNT(*) FROM post WHERE post.profile_id = profile.id)
                AS saved_post_count
        FROM profile
        """
    )
    abstract fun getProfileCounts(): Flow<List<ProfileCounts>>

    @Query("DELETE FROM profile  WHERE id = :id")
    abstract suspend fun deleteFromId(id: Int)
}
