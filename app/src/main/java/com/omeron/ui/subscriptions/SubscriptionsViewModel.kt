package com.omeron.ui.subscriptions

import com.omeron.data.model.db.Subscription
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
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    repository: PostListRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, repository) {

    private val _searchQuery: MutableStateFlow<String> = MutableStateFlow("")

    val filteredSubscriptions: Flow<List<Subscription>> = combine(
        subscriptions,
        _searchQuery
    ) { subscriptions, searchQuery ->
        subscriptions.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }.flowOn(defaultDispatcher)

    fun setSearchQuery(query: String) {
        _searchQuery.updateValue(query)
    }
}
