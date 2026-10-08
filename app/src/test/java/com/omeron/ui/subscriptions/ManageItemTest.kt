package com.omeron.ui.subscriptions

import com.omeron.R
import com.omeron.data.model.db.FollowedUser
import com.omeron.data.model.db.Multireddit
import com.omeron.data.model.db.MultiredditMember
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageItemTest {

    private fun subscription(name: String, hidden: Boolean = false) =
        Subscription(name = name, time = 0L, icon = null, hidden = hidden)

    private fun user(name: String, hidden: Boolean = false) =
        FollowedUser(name = name, icon = null, time = 0L, hidden = hidden)

    private fun multireddit(
        id: Long,
        name: String,
        hidden: Boolean = false,
        members: List<MultiredditMember> = emptyList()
    ) = MultiredditWithMembers(Multireddit(id = id, name = name, hidden = hidden), members)

    private fun member(multiredditId: Long, name: String, type: MultiredditMemberType) =
        MultiredditMember(multiredditId = multiredditId, targetName = name, type = type.value)

    private fun titles(items: List<ManageItem>): List<Int> =
        items.filterIsInstance<ManageItem.SectionLabel>().map { it.titleRes }

    @Test
    fun `communities are grouped into shown, people and hidden sections with counts`() {
        val items = buildManageCommunityItems(
            subscriptions = listOf(subscription("kotlin"), subscription("android"), subscription("news", hidden = true)),
            followedUsers = listOf(user("spez"), user("quiet", hidden = true)),
            multireddits = emptyList(),
            query = ""
        )

        assertEquals(
            listOf(
                ManageItem.SectionLabel(R.string.manage_section_shown, 2),
                ManageItem.SectionLabel(R.string.manage_section_people, 1),
                ManageItem.SectionLabel(R.string.manage_section_hidden_communities, 2)
            ),
            items.filterIsInstance<ManageItem.SectionLabel>()
        )
        val shownNames = items.filterIsInstance<ManageItem.CommunityRow>().take(3).map { it.name }
        assertEquals(listOf("android", "kotlin", "spez"), shownNames)
    }

    @Test
    fun `a section with no rows has no label`() {
        val items = buildManageCommunityItems(
            subscriptions = listOf(subscription("android")),
            followedUsers = emptyList(),
            multireddits = emptyList(),
            query = ""
        )

        assertEquals(listOf(R.string.manage_section_shown), titles(items))
    }

    @Test
    fun `filter ignores case and a filter matching nothing returns a message`() {
        val subscriptions = listOf(subscription("Android"), subscription("kotlin"))

        val matching = buildManageCommunityItems(subscriptions, emptyList(), emptyList(), "ANDR")
        val rows = matching.filterIsInstance<ManageItem.CommunityRow>()
        assertEquals(listOf("Android"), rows.map { it.name })

        val none = buildManageCommunityItems(subscriptions, emptyList(), emptyList(), "zzz")
        assertEquals(listOf<ManageItem>(ManageItem.Message(R.string.drawer_no_match)), none)
    }

    @Test
    fun `profile with no communities and no users gives an empty list`() {
        assertTrue(buildManageCommunityItems(emptyList(), emptyList(), emptyList(), "").isEmpty())
    }

    @Test
    fun `community row lists the multireddits it belongs to`() {
        val dev = multireddit(1, "Dev", members = listOf(member(1, "Android", MultiredditMemberType.SUBREDDIT)))
        val people = multireddit(2, "People", members = listOf(member(2, "android", MultiredditMemberType.USER)))

        val items = buildManageCommunityItems(
            subscriptions = listOf(subscription("android")),
            followedUsers = emptyList(),
            multireddits = listOf(dev, people),
            query = ""
        )

        val row = items.filterIsInstance<ManageItem.CommunityRow>().single()
        assertEquals(listOf("Dev"), row.multiredditNames)
    }

    @Test
    fun `multireddits are grouped into shown and hidden sections`() {
        val items = buildManageMultiredditItems(
            listOf(
                multireddit(1, "Photography"),
                multireddit(2, "News", hidden = true),
                multireddit(
                    3,
                    "Dev",
                    members = listOf(
                        member(3, "android", MultiredditMemberType.SUBREDDIT),
                        member(3, "spez", MultiredditMemberType.USER),
                        member(3, "kotlin", MultiredditMemberType.SUBREDDIT)
                    )
                )
            )
        )

        assertEquals(
            listOf(R.string.manage_section_shown, R.string.manage_section_hidden_multireddits),
            titles(items)
        )
        val dev = items.filterIsInstance<ManageItem.MultiredditRow>().first { it.name == "Dev" }
        assertEquals(3, dev.memberCount)
        assertEquals(listOf("r/android", "u/spez"), dev.memberPreviewNames)
    }

    @Test
    fun `no multireddits gives an empty list`() {
        assertTrue(buildManageMultiredditItems(emptyList()).isEmpty())
    }
}
