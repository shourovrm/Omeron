package com.omeron.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.omeron.data.model.db.FollowedUser
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FollowedUserDao : BaseDao<FollowedUser> {

    @Query("DELETE FROM followed_user WHERE name = :name AND profile_id = :profileId")
    abstract suspend fun deleteFromNameAndProfile(name: String, profileId: Int)

    @Query("SELECT * FROM followed_user WHERE profile_id = :profileId")
    abstract fun getFromProfile(profileId: Int): Flow<List<FollowedUser>>

    @Query("SELECT name FROM followed_user WHERE profile_id = :profileId AND hidden = 0")
    abstract fun getVisibleNamesFromProfile(profileId: Int): Flow<List<String>>

    @Query("UPDATE followed_user SET hidden = :hidden WHERE name = :name AND profile_id = :profileId")
    abstract suspend fun setHidden(name: String, profileId: Int, hidden: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM followed_user WHERE name = :name AND profile_id = :profileId)")
    abstract fun isFollowed(name: String, profileId: Int): Flow<Boolean>
}
