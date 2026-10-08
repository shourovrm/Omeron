package com.omeron.ui.drawer

import androidx.annotation.StringRes

/** One row of the community switcher list shown in the navigation drawer. */
sealed interface DrawerItem {

    /** The manage page a section's Manage link opens. */
    enum class ManageTarget { MULTIREDDITS, COMMUNITIES }

    data class SectionHeader(
        @StringRes val titleRes: Int,
        val manageTarget: ManageTarget
    ) : DrawerItem

    data class MultiredditRow(
        val multiredditId: Long,
        val name: String,
        val memberCount: Int
    ) : DrawerItem

    data class CommunityRow(val subredditName: String) : DrawerItem

    data class Message(@StringRes val textRes: Int) : DrawerItem
}
