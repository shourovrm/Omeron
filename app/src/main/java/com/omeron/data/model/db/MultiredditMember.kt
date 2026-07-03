package com.omeron.data.model.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "multireddit_member",
    foreignKeys = [
        ForeignKey(
            entity = Multireddit::class,
            parentColumns = ["id"],
            childColumns = ["multireddit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MultiredditMember(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "multireddit_id", index = true)
    val multiredditId: Long,

    @ColumnInfo(name = "target_name")
    val targetName: String,

    // MultiredditMemberType ordinal (0 = SUBREDDIT, 1 = USER)
    @ColumnInfo(name = "type")
    val type: Int
)
