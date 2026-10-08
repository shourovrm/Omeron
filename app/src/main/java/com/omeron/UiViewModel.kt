package com.omeron

import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.ui.base.BaseViewModel
import com.omeron.ui.drawer.DrawerItem
import com.omeron.ui.drawer.buildDrawerItems
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class UiViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val postListRepository: PostListRepository
) : BaseViewModel(preferencesRepository, postListRepository) {

    val leftHandedMode: Flow<Boolean> = preferencesRepository
        .getLeftHandedMode()
        .distinctUntilChanged()

    val policyDisclaimerShown: Flow<Boolean> = preferencesRepository
        .getPolicyDisclaimerShown(false)
        .distinctUntilChanged()

    private val _navigationVisibility = MutableStateFlow(true)
    val navigationVisibility: StateFlow<Boolean> = _navigationVisibility

    // Height of the bottom bar including the system navigation inset, published by MainActivity
    // so list screens shown above the bar can keep their last items clear of it.
    private val _bottomNavigationHeight = MutableStateFlow(0)
    val bottomNavigationHeight: StateFlow<Int> = _bottomNavigationHeight

    // Selected home tab: 0 = Feed, 1 = Popular, 2 = Multis. Set by the bottom
    // navigation bar (MainActivity); PostListFragment derives its feed mode from it.
    private val _homeTab = MutableStateFlow(0)
    val homeTab: StateFlow<Int> = _homeTab

    private val drawerFilterQuery = MutableStateFlow("")

    private val multireddits: Flow<List<MultiredditWithMembers>> =
        currentProfile.flatMapLatest { postListRepository.getMultireddits(it.id) }

    val drawerItems: Flow<List<DrawerItem>> = combine(
        multireddits,
        subscriptions,
        drawerFilterQuery
    ) { multireddits, subscriptions, query ->
        buildDrawerItems(multireddits, subscriptions, query.trim())
    }

    fun setDrawerFilterQuery(query: String) {
        drawerFilterQuery.updateValue(query)
    }

    fun setNavigationVisibility(visible: Boolean) {
        _navigationVisibility.updateValue(visible)
    }

    fun setBottomNavigationHeight(height: Int) {
        _bottomNavigationHeight.updateValue(height)
    }

    fun setHomeTab(tab: Int) {
        _homeTab.updateValue(tab)
    }
}
