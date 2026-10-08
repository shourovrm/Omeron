package com.omeron.ui.subscriptions

import androidx.annotation.StringRes
import com.omeron.R
import com.omeron.data.model.db.FollowedUser
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.Subscription

/** One row of the manage communities or manage multireddits list. */
sealed interface ManageItem {

    data class SectionLabel(@StringRes val titleRes: Int, val count: Int) : ManageItem

    data class CommunityRow(
        val name: String,
        val isUser: Boolean,
        val joinedTimeMillis: Long,
        val isHidden: Boolean,
        val multiredditNames: List<String>
    ) : ManageItem

    data class MultiredditRow(
        val id: Long,
        val name: String,
        val memberCount: Int,
        val memberPreviewNames: List<String>,
        val isHidden: Boolean
    ) : ManageItem

    data class Message(@StringRes val textRes: Int) : ManageItem
}

private const val MEMBER_PREVIEW_COUNT = 2

/**
 * Groups subscriptions and followed users into Shown, People you follow and Hidden sections.
 * Returns an empty list when the profile has neither, so the screen can show its empty state; a
 * filter that matches nothing returns a single message instead.
 */
fun buildManageCommunityItems(
    subscriptions: List<Subscription>,
    followedUsers: List<FollowedUser>,
    multireddits: List<MultiredditWithMembers>,
    query: String
): List<ManageItem> {
    // Blank names cannot be opened, so they never get a row
    val communityRows = subscriptions
        .filter { it.name.isNotBlank() }
        .map {
            ManageItem.CommunityRow(
                name = it.name,
                isUser = false,
                joinedTimeMillis = it.time,
                isHidden = it.hidden,
                multiredditNames = multiredditNamesContaining(multireddits, it.name, MultiredditMemberType.SUBREDDIT)
            )
        }
    val userRows = followedUsers
        .filter { it.name.isNotBlank() }
        .map {
            ManageItem.CommunityRow(
                name = it.name,
                isUser = true,
                joinedTimeMillis = it.time,
                isHidden = it.hidden,
                multiredditNames = multiredditNamesContaining(multireddits, it.name, MultiredditMemberType.USER)
            )
        }
    if (communityRows.isEmpty() && userRows.isEmpty()) return emptyList()

    val trimmedQuery = query.trim()
    val matchingRows = (communityRows + userRows)
        .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        .sortedWith(compareBy({ it.isUser }, { it.name.lowercase() }))
    if (matchingRows.isEmpty()) return listOf(ManageItem.Message(R.string.drawer_no_match))

    val shownCommunities = matchingRows.filter { !it.isUser && !it.isHidden }
    val shownUsers = matchingRows.filter { it.isUser && !it.isHidden }
    val hiddenRows = matchingRows.filter { it.isHidden }

    val items = mutableListOf<ManageItem>()
    items.addSection(R.string.manage_section_shown, shownCommunities)
    items.addSection(R.string.manage_section_people, shownUsers)
    items.addSection(R.string.manage_section_hidden_communities, hiddenRows)
    return items
}

/** Groups multireddits into Shown and Hidden sections; empty when there are none. */
fun buildManageMultiredditItems(multireddits: List<MultiredditWithMembers>): List<ManageItem> {
    val rows = multireddits.map { multireddit ->
        ManageItem.MultiredditRow(
            id = multireddit.multireddit.id,
            name = multireddit.multireddit.name,
            memberCount = multireddit.members.size,
            memberPreviewNames = multireddit.members
                .take(MEMBER_PREVIEW_COUNT)
                .map { member -> memberDisplayName(member.targetName, member.type) },
            isHidden = multireddit.multireddit.hidden
        )
    }

    val items = mutableListOf<ManageItem>()
    items.addSection(R.string.manage_section_shown, rows.filter { !it.isHidden })
    items.addSection(R.string.manage_section_hidden_multireddits, rows.filter { it.isHidden })
    return items
}

private fun MutableList<ManageItem>.addSection(@StringRes titleRes: Int, rows: List<ManageItem>) {
    if (rows.isEmpty()) return
    add(ManageItem.SectionLabel(titleRes, rows.size))
    addAll(rows)
}

private fun multiredditNamesContaining(
    multireddits: List<MultiredditWithMembers>,
    targetName: String,
    type: MultiredditMemberType
): List<String> {
    return multireddits
        .filter { multireddit ->
            multireddit.members.any {
                it.targetName.equals(targetName, ignoreCase = true) &&
                    MultiredditMemberType.fromValue(it.type) == type
            }
        }
        .map { it.multireddit.name }
}

private fun memberDisplayName(targetName: String, type: Int): String {
    val prefix = if (MultiredditMemberType.fromValue(type) == MultiredditMemberType.USER) "u/" else "r/"
    return prefix + targetName
}
