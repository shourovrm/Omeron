package com.omeron.ui.drawer

import com.omeron.R
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.Subscription

/**
 * Builds the drawer list from the shown multireddits and subscriptions that match [query].
 *
 * Hidden items are left out and never counted or mentioned; they only appear on the manage
 * pages. Both section headers are always present so their Manage links stay reachable.
 */
fun buildDrawerItems(
    multireddits: List<MultiredditWithMembers>,
    subscriptions: List<Subscription>,
    query: String
): List<DrawerItem> {
    val multiredditRows = multireddits
        .filter { !it.multireddit.hidden }
        .filter { it.multireddit.name.contains(query, ignoreCase = true) }
        .map { DrawerItem.MultiredditRow(it.multireddit.id, it.multireddit.name, it.members.size) }

    // Blank names cannot be opened, so they never get a row
    val shownNames = subscriptions
        .filter { !it.hidden }
        .map { it.name }
        .filter { it.isNotBlank() }
    val communityRows = shownNames
        .filter { it.contains(query, ignoreCase = true) }
        .sortedBy { it.lowercase() }
        .map { DrawerItem.CommunityRow(it) }

    val items = mutableListOf<DrawerItem>()
    items += DrawerItem.SectionHeader(
        R.string.drawer_section_multireddits,
        DrawerItem.ManageTarget.MULTIREDDITS
    )
    items += multiredditRows
    items += DrawerItem.SectionHeader(
        R.string.drawer_section_communities,
        DrawerItem.ManageTarget.COMMUNITIES
    )
    when {
        shownNames.isEmpty() -> items += DrawerItem.Message(R.string.drawer_no_subscriptions)
        communityRows.isEmpty() -> items += DrawerItem.Message(R.string.drawer_no_match)
        else -> items += communityRows
    }
    return items
}
