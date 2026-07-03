package com.omeron.data.model.backup

import androidx.annotation.DrawableRes
import com.omeron.R

enum class BackupType(
    val displayName: String,
    @DrawableRes val icon: Int,
    val mime: Array<String>
) {
    OMERON("Omeron", R.drawable.ic_omeron, arrayOf("application/json")),
    REDDIT("Reddit", R.drawable.ic_reddit, arrayOf("application/json"))
}
