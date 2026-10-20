package com.omeron.ui.profile

import androidx.lifecycle.ViewModel
import com.omeron.data.model.preferences.ProfileTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Holds what is selected on the profile tabs. It is scoped to the activity, not to the profile
 * screen: the tab fragments and their views are destroyed whenever the user leaves the screen,
 * and the screen's own view model lives only as long as the navigation library keeps the profile
 * graph's entry, which differs between going deeper and switching menu destinations. An activity
 * scope does not depend on either. The selection is lost only when the app process dies.
 */
@HiltViewModel
class ProfileSelectionViewModel @Inject constructor() : ViewModel() {

    private val _selection = MutableStateFlow(ProfileSelection())
    val selection: StateFlow<ProfileSelection> get() = _selection

    fun toggle(tab: ProfileTab, rowId: String) {
        _selection.update { it.toggle(tab, rowId) }
    }

    fun toggleSelectAll(tab: ProfileTab, visibleIds: Collection<String>) {
        _selection.update { it.toggleSelectAll(tab, visibleIds) }
    }

    fun leave(tab: ProfileTab) {
        _selection.update { it.leave(tab) }
    }

    fun retainExisting(tab: ProfileTab, existingIds: Set<String>) {
        _selection.update { it.retainExisting(tab, existingIds) }
    }
}
