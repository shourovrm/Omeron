package com.omeron

import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@HiltViewModel
class UiViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    postListRepository: PostListRepository
) : BaseViewModel(preferencesRepository, postListRepository) {

    val leftHandedMode: Flow<Boolean> = preferencesRepository
        .getLeftHandedMode()
        .distinctUntilChanged()

    val policyDisclaimerShown: Flow<Boolean> = preferencesRepository
        .getPolicyDisclaimerShown(false)
        .distinctUntilChanged()

    private val _navigationVisibility = MutableStateFlow(true)
    val navigationVisibility: StateFlow<Boolean> = _navigationVisibility

    // Selected home tab: 0 = Feed, 1 = Popular, 2 = Multis. Shared between the bottom
    // navigation bar (MainActivity) and the hidden TabLayout in PostListFragment.
    private val _homeTab = MutableStateFlow(0)
    val homeTab: StateFlow<Int> = _homeTab

    fun setNavigationVisibility(visible: Boolean) {
        _navigationVisibility.updateValue(visible)
    }

    fun setHomeTab(tab: Int) {
        _homeTab.updateValue(tab)
    }
}
