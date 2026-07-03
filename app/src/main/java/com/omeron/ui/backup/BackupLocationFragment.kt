package com.omeron.ui.backup

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.navigation.navGraphViewModels
import com.omeron.R
import com.omeron.data.model.backup.BackupType
import com.omeron.databinding.FragmentBackupLocationBinding
import com.omeron.ui.backup.BackupFragment.Operation.BACKUP
import com.omeron.ui.backup.BackupFragment.Operation.RESTORE
import com.omeron.ui.base.BaseFragment
import com.omeron.util.DateUtil
import com.omeron.util.extension.getFilename
import com.omeron.util.extension.launchRepeat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Date

@AndroidEntryPoint
class BackupLocationFragment : BaseFragment() {

    private var _binding: FragmentBackupLocationBinding? = null
    private val binding get() = _binding!!

    private val backupViewModel: BackupViewModel by navGraphViewModels(R.id.backup)

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
        this::setUri
    )

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        this::setUri
    )

    private val filename: String
        get() = getString(R.string.app_name) +
                "_" +
                DateUtil.getFormattedDate(getString(R.string.file_date_format), Date()) +
                ".json"

    private val mimeType: Array<String>
        get() {
            val type = backupViewModel.backupType.value?.mime ?: return arrayOf("*/*")

            if (
                type.contains("application/json") &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            ) {
                // application/json is not support on API <= 28, so application/octet-stream is used
                // as a fallback
                return arrayOf(*type, "application/octet-stream")
            }

            return type
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViewModel()

        binding.pickButton.setOnClickListener {
            backupViewModel.operation.value?.let { operation ->
                pickFile(operation)
            }
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                combine(
                    backupViewModel.operation,
                    backupViewModel.backupType
                ) { operation, type ->
                    setExplanationText(operation, type)
                }.collect()
            }

            launch {
                backupViewModel.chosenUri.collect { uri ->
                    binding.textFilename.text = uri?.getFilename(requireContext())
                }
            }
        }
    }

    private fun setExplanationText(operation: BackupFragment.Operation?, backupType: BackupType?) {
        binding.textExplanation.text = when (operation) {
            BACKUP -> getString(R.string.backup_location_explanation_backup)
            RESTORE -> backupType?.let { getRestoreExplanationText(it) }
            else -> ""
        }
    }

    private fun getRestoreExplanationText(backupType: BackupType): String {
        return when (backupType) {
            BackupType.OMERON -> getString(R.string.backup_location_explanation_restore_stealth)
            BackupType.REDDIT -> getString(R.string.backup_location_explanation_restore_reddit)
        }
    }

    private fun pickFile(operation: BackupFragment.Operation) {
        when (operation) {
            BACKUP -> {
                createDocument.launch(filename)
            }
            else -> {
                openDocument.launch(mimeType)
            }
        }
    }

    private fun setUri(uri: Uri?) {
        backupViewModel.setUri(uri)
    }

    override fun onBackPressed() {
        // Disabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
