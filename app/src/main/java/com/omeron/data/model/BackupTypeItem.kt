package com.omeron.data.model

import com.omeron.data.model.backup.BackupType

data class BackupTypeItem(
    val type: BackupType,

    var selected: Boolean = false
)
