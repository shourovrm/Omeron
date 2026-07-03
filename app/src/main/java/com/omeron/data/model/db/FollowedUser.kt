package com.omeron.data.model.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "followed_user",
    primaryKeys = ["name", "profile_id"],
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FollowedUser(
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String?,

    @ColumnInfo(name = "time")
    val time: Long,

    @ColumnInfo(name = "hidden", defaultValue = "0")
    var hidden: Boolean = false,

    @ColumnInfo(name = "profile_id", index = true)
    var profileId: Int = 1
)
