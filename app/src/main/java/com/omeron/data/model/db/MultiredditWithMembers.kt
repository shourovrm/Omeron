package com.omeron.data.model.db

import androidx.room.Embedded
import androidx.room.Relation

data class MultiredditWithMembers(
    @Embedded
    val multireddit: Multireddit,

    @Relation(parentColumn = "id", entityColumn = "multireddit_id")
    val members: List<MultiredditMember>
)
