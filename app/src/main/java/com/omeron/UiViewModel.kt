package com.omeron

import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.Subscription
import com.omeron.ui.base.BaseViewModel
import com.omeron.ui.drawer.DrawerItem
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

    private fun buildDrawerItems(
        multireddits: List<MultiredditWithMembers>,
        subscriptions: List<Subscription>,
        query: String
    ): List<DrawerItem> {
        val multiredditRows = multireddits
            .filter { it.multireddit.name.contains(query, ignoreCase = true) }
            .map { DrawerItem.MultiredditRow(it.multireddit.id, it.multireddit.name, it.members.size) }

        // Blank names cannot be opened, so they never get a row
        val subscribedNames = subscriptions.map { it.name }.filter { it.isNotBlank() }
        val communityRows = subscribedNames
            .filter { it.contains(query, ignoreCase = true) }
            .sortedBy { it.lowercase() }
            .map { DrawerItem.CommunityRow(it) }

        val items = mutableListOf<DrawerItem>()
        if (multiredditRows.isNotEmpty()) {
            items += DrawerItem.SectionHeader(R.string.drawer_section_multireddits)
            items += multiredditRows
        }
        items += DrawerItem.SectionHeader(R.string.drawer_section_communities)
        when {
            subscribedNames.isEmpty() -> items += DrawerItem.Message(R.string.drawer_no_subscriptions)
            communityRows.isEmpty() -> items += DrawerItem.Message(R.string.drawer_no_match)
            else -> items += communityRows
        }
        return items
    }

    fun setNavigationVisibility(visible: Boolean) {
        _navigationVisibility.updateValue(visible)
    }

    fun setHomeTab(tab: Int) {
        _homeTab.updateValue(tab)
    }
}
