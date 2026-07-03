package com.omeron.ui.subscriptions

import androidx.lifecycle.viewModelScope
import com.omeron.data.model.db.FollowedUser
import com.omeron.data.model.db.Multireddit
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.Subscription
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.extension.latest
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val repository: PostListRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    private val _searchQuery: MutableStateFlow<String> = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredSubscriptions: Flow<List<Subscription>> = combine(
        subscriptions,
        _searchQuery
    ) { subscriptions, searchQuery ->
        subscriptions.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }.flowOn(defaultDispatcher)

    val followedUsers: Flow<List<FollowedUser>> = currentProfile.flatMapLatest {
        repository.getFollowedUsers(it.id)
    }

    val multireddits: Flow<List<MultiredditWithMembers>> = currentProfile.flatMapLatest {
        repository.getMultireddits(it.id)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.updateValue(query)
    }

    fun toggleSubscriptionHidden(subscription: Subscription) {
        viewModelScope.launch {
            currentProfile.latest?.let {
                repository.setSubscriptionHidden(subscription.name, it.id, !subscription.hidden)
            }
        }
    }

    fun toggleUserHidden(user: FollowedUser) {
        viewModelScope.launch {
            currentProfile.latest?.let {
                repository.setUserHidden(user.name, it.id, !user.hidden)
            }
        }
    }

    fun unfollowUser(user: FollowedUser) {
        viewModelScope.launch {
            currentProfile.latest?.let { repository.unfollowUser(user.name, it.id) }
        }
    }

    fun deleteMultireddit(id: Long) {
        viewModelScope.launch { repository.deleteMultireddit(id) }
    }

    fun toggleMultiredditHidden(multireddit: Multireddit) {
        viewModelScope.launch {
            repository.setMultiredditHidden(multireddit.id, !multireddit.hidden)
        }
    }
}
