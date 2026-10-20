package com.omeron.ui.profile

import com.omeron.data.model.preferences.ProfileTab

/** How the select-all box looks: empty, with a dash, or ticked. */
enum class SelectionBoxState { NONE, SOME, ALL }

/**
 * The rows picked on each profile tab. A tab is in selection mode when it has an entry in the
 * map, even an empty one: the user can untick the last row and stay in the mode until they leave
 * it. Rows are identified by a stable id (post id, comment name, history post id), never by list
 * position, so the selection survives the list changing underneath it.
 */
data class ProfileSelection(
    private val selectedIdsByTab: Map<ProfileTab, Set<String>> = emptyMap()
) {

    fun isActive(tab: ProfileTab): Boolean = selectedIdsByTab.containsKey(tab)

    fun selectedIds(tab: ProfileTab): Set<String> = selectedIdsByTab[tab] ?: emptySet()

    /** Selects or unselects [id]. On a tab that is not in selection mode this turns the mode on. */
    fun toggle(tab: ProfileTab, id: String): ProfileSelection {
        val selected = selectedIds(tab)
        return withSelectedIds(tab, if (id in selected) selected - id else selected + id)
    }

    fun boxState(tab: ProfileTab, visibleIds: Collection<String>): SelectionBoxState {
        val selected = selectedIds(tab)
        val selectedVisibleCount = visibleIds.count { it in selected }
        return when {
            selectedVisibleCount == 0 -> SelectionBoxState.NONE
            selectedVisibleCount == visibleIds.size -> SelectionBoxState.ALL
            else -> SelectionBoxState.SOME
        }
    }

    /**
     * Selects every visible row, or unselects them when they are all selected already. Rows the
     * search filter hides keep the state they had.
     */
    fun toggleSelectAll(tab: ProfileTab, visibleIds: Collection<String>): ProfileSelection {
        val selected = selectedIds(tab)
        val allVisibleSelected = boxState(tab, visibleIds) == SelectionBoxState.ALL
        return withSelectedIds(
            tab,
            if (allVisibleSelected) selected - visibleIds.toSet() else selected + visibleIds
        )
    }

    fun leave(tab: ProfileTab): ProfileSelection {
        return ProfileSelection(selectedIdsByTab - tab)
    }

    /**
     * Drops selected ids that are not in [existingIds] any more (a post unsaved on another screen,
     * for example). When that empties the selection, the tab leaves the mode, because there is
     * nothing left to act on and the user did not choose to clear it.
     */
    fun retainExisting(tab: ProfileTab, existingIds: Set<String>): ProfileSelection {
        if (!isActive(tab)) return this

        val selected = selectedIds(tab)
        val kept = selected.filterTo(mutableSetOf()) { it in existingIds }
        return when {
            kept.size == selected.size -> this
            kept.isEmpty() -> leave(tab)
            else -> withSelectedIds(tab, kept)
        }
    }

    private fun withSelectedIds(tab: ProfileTab, ids: Set<String>): ProfileSelection {
        return ProfileSelection(selectedIdsByTab + (tab to ids))
    }
}
