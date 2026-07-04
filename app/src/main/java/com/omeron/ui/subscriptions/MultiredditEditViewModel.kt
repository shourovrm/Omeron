package com.omeron.ui.subscriptions

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.data.repository.PostListRepository
import com.omeron.data.repository.PreferencesRepository
import com.omeron.ui.base.BaseViewModel
import com.omeron.util.extension.latest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// Backs MultiredditEditDialogFragment for both "create new" (id == null) and "edit existing"
// (id set) - see the dialog for why members are buffered locally until Save in create mode.
@HiltViewModel
class MultiredditEditViewModel @Inject constructor(
    private val repository: PostListRepository,
    preferencesRepository: PreferencesRepository
) : BaseViewModel(preferencesRepository, repository) {

    private val _id: MutableStateFlow<Long?> = MutableStateFlow(null)

    private val existing: Flow<MultiredditWithMembers?> = _id.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            currentProfile.flatMapLatest { repository.getMultireddits(it.id) }
                .map { list -> list.find { it.multireddit.id == id } }
        }
    }

    // ponytail: create mode has no row yet to attach members to, so pending adds/removes are
    // buffered here and only persisted on Save. Edit mode ignores this - `existing` already
    // reflects each add/remove immediately since those call the repo directly.
    private val _pendingSubreddits: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    private val _pendingUsers: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

    val subredditMembers: Flow<List<String>> = combine(existing, _pendingSubreddits) { multi, pending ->
        multi?.let { membersOf(it, MultiredditMemberType.SUBREDDIT) } ?: pending
    }

    val userMembers: Flow<List<String>> = combine(existing, _pendingUsers) { multi, pending ->
        multi?.let { membersOf(it, MultiredditMemberType.USER) } ?: pending
    }

    private fun membersOf(multi: MultiredditWithMembers, type: MultiredditMemberType): List<String> {
        return multi.members
            .filter { MultiredditMemberType.fromValue(it.type) == type }
            .map { it.targetName }
    }

    fun setId(id: Long?) {
        _id.value = id
    }

    suspend fun getInitialName(): String? {
        val id = _id.value ?: return null
        val profileId = currentProfile.latest?.id ?: return null
        return repository.getMultireddits(profileId).first()
            .find { it.multireddit.id == id }
            ?.multireddit?.name
    }

    fun addSubreddit(name: String) {
        val id = _id.value
        if (id == null) {
            _pendingSubreddits.value = _pendingSubreddits.value + name
        } else {
            viewModelScope.launch { repository.addMember(id, name, MultiredditMemberType.SUBREDDIT) }
        }
    }

    fun removeSubreddit(name: String) {
        val id = _id.value
        if (id == null) {
            _pendingSubreddits.value = _pendingSubreddits.value - name
        } else {
            viewModelScope.launch { repository.removeMember(id, name, MultiredditMemberType.SUBREDDIT) }
        }
    }

    fun addUser(name: String) {
        val id = _id.value
        if (id == null) {
            _pendingUsers.value = _pendingUsers.value + name
        } else {
            viewModelScope.launch { repository.addMember(id, name, MultiredditMemberType.USER) }
        }
    }

    fun removeUser(name: String) {
        val id = _id.value
        if (id == null) {
            _pendingUsers.value = _pendingUsers.value - name
        } else {
            viewModelScope.launch { repository.removeMember(id, name, MultiredditMemberType.USER) }
        }
    }

    fun searchSubreddits(query: String): Flow<PagingData<Child>> {
        return repository.searchSubreddit(query, Sorting(Sort.RELEVANCE))
    }

    // Buffered create-mode members, read by the dialog on Save so the persist can run in the
    // activity-scoped SubscriptionsViewModel (this dialog VM is cancelled on dismiss).
    fun pendingMembers(): Pair<List<String>, List<String>> =
        _pendingSubreddits.value to _pendingUsers.value

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteMultireddit(id) }
    }
}
