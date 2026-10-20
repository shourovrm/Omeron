package com.omeron.ui.profile

import com.omeron.data.model.preferences.ProfileTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSelectionTest {

    private val visibleIds = listOf("a", "b", "c")

    @Test
    fun `a new selection has no tab in selection mode`() {
        val selection = ProfileSelection()

        ProfileTab.values().forEach { assertFalse(selection.isActive(it)) }
        assertEquals(emptySet<String>(), selection.selectedIds(ProfileTab.HISTORY))
    }

    @Test
    fun `toggling a row in a tab that is not in selection mode selects it and turns the mode on for that tab only`() {
        val selection = ProfileSelection().toggle(ProfileTab.SAVED_POSTS, "a")

        assertTrue(selection.isActive(ProfileTab.SAVED_POSTS))
        assertEquals(setOf("a"), selection.selectedIds(ProfileTab.SAVED_POSTS))
        assertFalse(selection.isActive(ProfileTab.HISTORY))
    }

    @Test
    fun `toggle adds and removes a row and keeps the mode on when the last row is removed`() {
        val selection = ProfileSelection()
            .toggle(ProfileTab.SAVED_POSTS, "a")
            .toggle(ProfileTab.SAVED_POSTS, "b")

        assertEquals(setOf("a", "b"), selection.selectedIds(ProfileTab.SAVED_POSTS))

        val emptied = selection
            .toggle(ProfileTab.SAVED_POSTS, "a")
            .toggle(ProfileTab.SAVED_POSTS, "b")

        assertEquals(emptySet<String>(), emptied.selectedIds(ProfileTab.SAVED_POSTS))
        assertTrue(emptied.isActive(ProfileTab.SAVED_POSTS))
    }

    @Test
    fun `each tab keeps its own selected rows`() {
        val selection = ProfileSelection()
            .toggle(ProfileTab.SAVED_POSTS, "a")
            .toggle(ProfileTab.HISTORY, "b")

        assertEquals(setOf("a"), selection.selectedIds(ProfileTab.SAVED_POSTS))
        assertEquals(setOf("b"), selection.selectedIds(ProfileTab.HISTORY))
    }

    @Test
    fun `box state follows how many of the visible rows are selected`() {
        val tab = ProfileTab.SAVED_POSTS
        val none = ProfileSelection().toggle(tab, "a").toggle(tab, "a")
        val some = none.toggle(tab, "a")
        val all = some.toggle(tab, "b").toggle(tab, "c")

        assertEquals(SelectionBoxState.NONE, none.boxState(tab, visibleIds))
        assertEquals(SelectionBoxState.SOME, some.boxState(tab, visibleIds))
        assertEquals(SelectionBoxState.ALL, all.boxState(tab, visibleIds))
    }

    @Test
    fun `box state is none when nothing is visible`() {
        val selection = ProfileSelection().toggle(ProfileTab.HISTORY, "a")

        assertEquals(SelectionBoxState.NONE, selection.boxState(ProfileTab.HISTORY, emptyList()))
    }

    @Test
    fun `select all box selects every visible row unless all are selected`() {
        val tab = ProfileTab.SAVED_COMMENTS
        val someSelected = ProfileSelection().toggle(tab, "a")

        val allSelected = someSelected.toggleSelectAll(tab, visibleIds)
        assertEquals(setOf("a", "b", "c"), allSelected.selectedIds(tab))

        val noneSelected = allSelected.toggleSelectAll(tab, visibleIds)
        assertEquals(emptySet<String>(), noneSelected.selectedIds(tab))
        assertTrue(noneSelected.isActive(tab))
    }

    @Test
    fun `select all leaves rows hidden by the filter as they were`() {
        val tab = ProfileTab.SAVED_POSTS
        val selection = ProfileSelection().toggle(tab, "hidden")

        val allVisible = selection.toggleSelectAll(tab, listOf("a", "b"))
        assertEquals(setOf("hidden", "a", "b"), allVisible.selectedIds(tab))

        val noneVisible = allVisible.toggleSelectAll(tab, listOf("a", "b"))
        assertEquals(setOf("hidden"), noneVisible.selectedIds(tab))
    }

    @Test
    fun `leave clears the selection of that tab only`() {
        val selection = ProfileSelection()
            .toggle(ProfileTab.SAVED_POSTS, "a")
            .toggle(ProfileTab.HISTORY, "b")
            .leave(ProfileTab.SAVED_POSTS)

        assertFalse(selection.isActive(ProfileTab.SAVED_POSTS))
        assertEquals(emptySet<String>(), selection.selectedIds(ProfileTab.SAVED_POSTS))
        assertTrue(selection.isActive(ProfileTab.HISTORY))
    }

    @Test
    fun `retain existing drops ids that are gone`() {
        val tab = ProfileTab.SAVED_POSTS
        val selection = ProfileSelection()
            .toggle(tab, "a")
            .toggle(tab, "b")
            .retainExisting(tab, setOf("b", "c"))

        assertEquals(setOf("b"), selection.selectedIds(tab))
        assertTrue(selection.isActive(tab))
    }

    @Test
    fun `retain existing leaves the mode when the last selected row is gone`() {
        val tab = ProfileTab.HISTORY
        val selection = ProfileSelection()
            .toggle(tab, "a")
            .retainExisting(tab, setOf("c"))

        assertFalse(selection.isActive(tab))
    }

    @Test
    fun `retain existing keeps the mode when the user had deselected every row`() {
        val tab = ProfileTab.HISTORY
        val selection = ProfileSelection()
            .toggle(tab, "a")
            .toggle(tab, "a")
            .retainExisting(tab, setOf("c"))

        assertTrue(selection.isActive(tab))
    }

    @Test
    fun `retain existing ignores a tab that is not in selection mode`() {
        val selection = ProfileSelection().retainExisting(ProfileTab.HISTORY, emptySet())

        assertEquals(ProfileSelection(), selection)
    }

    @Test
    fun `the box state of a group compares only that group's ids with the selection`() {
        val selected = setOf("a", "x")

        assertEquals(SelectionBoxState.NONE, selectionBoxState(selected, listOf("b", "c")))
        assertEquals(SelectionBoxState.SOME, selectionBoxState(selected, listOf("a", "b")))
        assertEquals(SelectionBoxState.ALL, selectionBoxState(selected, listOf("a")))
    }
}
