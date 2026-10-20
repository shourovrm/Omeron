package com.omeron.ui.profile

import com.omeron.R
import com.omeron.data.model.preferences.ProfileTab

/** How the select-all box looks: empty, with a dash, or ticked. */
enum class SelectionBoxState { NONE, SOME, ALL }

/** The drawable for this box state, shared by the selection bar and the history day labels. */
fun SelectionBoxState.iconRes(): Int = when (this) {
    SelectionBoxState.NONE -> R.drawable.ic_check_box_outline_blank
    SelectionBoxState.SOME -> R.drawable.ic_indeterminate_check_box
    SelectionBoxState.ALL -> R.drawable.ic_check_box
}

/**
 * The box for one group of rows (a whole tab, or one day of history): ticked when every row of
 * [groupIds] is in [selectedIds], a dash when some are, empty when none are or the group is empty.
 */
fun selectionBoxState(selectedIds: Set<String>, groupIds: Collection<String>): SelectionBoxState {
    val selectedCount = groupIds.count { it in selectedIds }
    return when {
        selectedCount == 0 -> SelectionBoxState.NONE
        selectedCount == groupIds.size -> SelectionBoxState.ALL
        else -> SelectionBoxState.SOME
    }
}

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
        return selectionBoxState(selectedIds(tab), visibleIds)
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
