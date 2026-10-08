package com.omeron.ui.drawer

import com.omeron.R
import com.omeron.data.model.db.Multireddit
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerItemsTest {

    private fun subscription(name: String, hidden: Boolean = false) =
        Subscription(name = name, time = 0L, icon = null, hidden = hidden)

    private fun multireddit(id: Long, name: String, hidden: Boolean = false) =
        MultiredditWithMembers(Multireddit(id = id, name = name, hidden = hidden), emptyList())

    @Test
    fun `hidden multireddits and subscriptions are left out`() {
        val items = buildDrawerItems(
            multireddits = listOf(multireddit(1, "Dev"), multireddit(2, "News", hidden = true)),
            subscriptions = listOf(subscription("kotlin"), subscription("gaming", hidden = true)),
            query = ""
        )

        assertEquals(listOf("Dev"), items.filterIsInstance<DrawerItem.MultiredditRow>().map { it.name })
        assertEquals(
            listOf("kotlin"),
            items.filterIsInstance<DrawerItem.CommunityRow>().map { it.subredditName }
        )
    }

    @Test
    fun `nothing in the list refers to hidden items`() {
        val items = buildDrawerItems(
            multireddits = listOf(multireddit(2, "News", hidden = true)),
            subscriptions = listOf(subscription("gaming", hidden = true)),
            query = ""
        )

        // Headers plus one message; no row, count or extra message mentions the hidden items.
        assertEquals(
            listOf(
                DrawerItem.SectionHeader(R.string.drawer_section_multireddits, DrawerItem.ManageTarget.MULTIREDDITS),
                DrawerItem.SectionHeader(R.string.drawer_section_communities, DrawerItem.ManageTarget.COMMUNITIES),
                DrawerItem.Message(R.string.drawer_no_subscriptions)
            ),
            items
        )
    }

    @Test
    fun `both section headers stay when there are no multireddits`() {
        val items = buildDrawerItems(emptyList(), listOf(subscription("kotlin")), "")

        val headers = items.filterIsInstance<DrawerItem.SectionHeader>()
        assertEquals(
            listOf(DrawerItem.ManageTarget.MULTIREDDITS, DrawerItem.ManageTarget.COMMUNITIES),
            headers.map { it.manageTarget }
        )
    }

    @Test
    fun `filter matches shown communities regardless of case`() {
        val items = buildDrawerItems(emptyList(), listOf(subscription("Kotlin"), subscription("android")), "KOT")

        assertEquals(
            listOf("Kotlin"),
            items.filterIsInstance<DrawerItem.CommunityRow>().map { it.subredditName }
        )
    }

    @Test
    fun `a filter with no match shows the no match message`() {
        val items = buildDrawerItems(emptyList(), listOf(subscription("kotlin")), "zzz")

        assertTrue(items.contains(DrawerItem.Message(R.string.drawer_no_match)))
    }
}
