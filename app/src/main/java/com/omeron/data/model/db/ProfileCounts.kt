package com.omeron.data.model.db

import androidx.room.ColumnInfo

/** What one profile holds, shown next to it in the profile list. */
data class ProfileCounts(
    @ColumnInfo(name = "profile_id")
    val profileId: Int,

    @ColumnInfo(name = "community_count")
    val communityCount: Int,

    @ColumnInfo(name = "saved_post_count")
    val savedPostCount: Int
)
