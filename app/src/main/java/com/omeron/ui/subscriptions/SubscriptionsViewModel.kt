package com.omeron.ui.subscriptions

import androidx.lifecycle.viewModelScope
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// Activity-scoped so the drawer, both manage pages and the multireddit edit page share it, and
// so its writes outlive the dialog or page that started them.
//
// Writes read the profile with first() instead of the replay cache: the drawer calls in here
// while nothing collects this ViewModel's currentProfile, which leaves the cache empty.
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val repository: PostListRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    private val communityFilterQuery: MutableStateFlow<String> = MutableStateFlow("")

    private val multireddits: Flow<List<MultiredditWithMembers>> = currentProfile.flatMapLatest {
        repository.getMultireddits(it.id)
    }

    private val followedUsers = currentProfile.flatMapLatest {
        repository.getFollowedUsers(it.id)
    }

    val manageCommunityItems: Flow<List<ManageItem>> = combine(
        subscriptions,
        followedUsers,
        multireddits,
        communityFilterQuery
    ) { subscriptions, followedUsers, multireddits, query ->
        buildManageCommunityItems(subscriptions, followedUsers, multireddits, query)
    }.flowOn(defaultDispatcher)

    val manageMultiredditItems: Flow<List<ManageItem>> = multireddits.map(::buildManageMultiredditItems)

    fun setCommunityFilterQuery(query: String) {
        communityFilterQuery.updateValue(query)
    }

    fun setSubscriptionHidden(name: String, hidden: Boolean) {
        viewModelScope.launch {
            repository.setSubscriptionHidden(name, currentProfile.first().id, hidden)
        }
    }

    fun unsubscribe(name: String) {
        viewModelScope.launch { repository.unsubscribe(name, currentProfile.first().id) }
    }

    // ponytail: same one-shot-snapshot pattern as SubredditViewModel.getMultiredditsSnapshot -
    // the picker dialog doesn't need a live flow while it's open.
    suspend fun getMultiredditsSnapshot(): List<MultiredditWithMembers> {
        return repository.getMultireddits(currentProfile.first().id).first()
    }

    fun addTargetToMultireddit(multiId: Long, target: String) {
        viewModelScope.launch { repository.addMember(multiId, target, MultiredditMemberType.SUBREDDIT) }
    }

    fun removeTargetFromMultireddit(multiId: Long, target: String) {
        viewModelScope.launch { repository.removeMember(multiId, target, MultiredditMemberType.SUBREDDIT) }
    }

    fun createMultiredditWithTarget(name: String, target: String) {
        viewModelScope.launch {
            val multiId = repository.createMultireddit(name, currentProfile.first().id)
            repository.addMember(multiId, target, MultiredditMemberType.SUBREDDIT)
        }
    }

    // Create/rename must run here (activity-scoped) not in the edit page's own ViewModel:
    // the page dismisses immediately after Save, cancelling its viewModelScope before the
    // async DB writes finish. This scope outlives the page.
    fun createMultireddit(name: String, subreddits: List<String>, users: List<String>) {
        viewModelScope.launch {
            val multiId = repository.createMultireddit(name, currentProfile.first().id)
            subreddits.forEach { repository.addMember(multiId, it, MultiredditMemberType.SUBREDDIT) }
            users.forEach { repository.addMember(multiId, it, MultiredditMemberType.USER) }
        }
    }

    fun renameMultireddit(id: Long, name: String) {
        viewModelScope.launch { repository.renameMultireddit(id, name) }
    }

    fun setUserHidden(name: String, hidden: Boolean) {
        viewModelScope.launch {
            repository.setUserHidden(name, currentProfile.first().id, hidden)
        }
    }

    fun unfollowUser(name: String) {
        viewModelScope.launch { repository.unfollowUser(name, currentProfile.first().id) }
    }

    fun deleteMultireddit(id: Long) {
        viewModelScope.launch { repository.deleteMultireddit(id) }
    }

    fun setMultiredditHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { repository.setMultiredditHidden(id, hidden) }
    }
}
