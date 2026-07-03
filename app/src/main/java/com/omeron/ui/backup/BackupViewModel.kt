package com.omeron.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omeron.data.model.Resource
import com.omeron.data.model.backup.BackupType
import com.omeron.data.model.backup.Profile
import com.omeron.data.repository.BackupRepository
import com.omeron.ui.backup.BackupFragment.Operation.BACKUP
import com.omeron.util.Util
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _page: MutableStateFlow<Int> = MutableStateFlow(0)
    val page: StateFlow<Int> get() = _page.asStateFlow()

    private val _operation: MutableStateFlow<BackupFragment.Operation?> = MutableStateFlow(null)
    val operation: StateFlow<BackupFragment.Operation?> get() = _operation.asStateFlow()

    private val _backupType: MutableStateFlow<BackupType?> = MutableStateFlow(null)
    val backupType: StateFlow<BackupType?> get() = _backupType.asStateFlow()

    private val _chosenUri: MutableStateFlow<Uri?> = MutableStateFlow(null)
    val chosenUri: StateFlow<Uri?> get() = _chosenUri.asStateFlow()

    private val _operationStatus: MutableStateFlow<Resource<List<Profile>>> =
        MutableStateFlow(Resource.Loading())
    val operationStatus: StateFlow<Resource<List<Profile>>> get() = _operationStatus.asStateFlow()

    private var operationJob: Job? = null

    fun runOperation() {
        Util.let(operation.value, chosenUri.value) { operation, uri ->
            when (operation) {
                BACKUP -> {
                    operationJob?.cancel()
                    operationJob = backup(uri)
                }
                else -> {
                    backupType.value?.let { type ->
                        operationJob?.cancel()
                        operationJob = restore(type, uri)
                    }
                }
            }
        }
    }

    private fun backup(uri: Uri): Job {
        return viewModelScope.launch {
            backupRepository.exportProfiles(uri, BackupType.OMERON)
                .collect {
                    _operationStatus.value = it
                }
        }
    }

    private fun restore(type: BackupType, uri: Uri): Job {
        return viewModelScope.launch {
            backupRepository.importProfiles(uri, type)
                .collect {
                    _operationStatus.value = it
                }
        }
    }

    fun setPage(position: Int) {
        _page.updateValue(position)
    }

    fun setOperation(operation: BackupFragment.Operation) {
        _operation.updateValue(operation)
    }

    fun setBackupType(backupType: BackupType?) {
        _backupType.updateValue(backupType)
    }

    fun setUri(uri: Uri?) {
        _chosenUri.updateValue(uri)
    }
}
