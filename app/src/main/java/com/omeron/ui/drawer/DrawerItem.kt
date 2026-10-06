package com.omeron.ui.drawer

import androidx.annotation.StringRes

/** One row of the community switcher list shown in the navigation drawer. */
sealed interface DrawerItem {

    data class SectionHeader(@StringRes val titleRes: Int) : DrawerItem

    data class MultiredditRow(
        val multiredditId: Long,
        val name: String,
        val memberCount: Int
    ) : DrawerItem

    data class CommunityRow(val subredditName: String) : DrawerItem

    data class Message(@StringRes val textRes: Int) : DrawerItem
}
