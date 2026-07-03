package com.omeron.ui.common.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.R
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.databinding.DialogAddProfileBinding
import com.omeron.util.extension.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ponytail: plain builder helper (not a DialogFragment) - matches how every other dialog in
// this app is shown (see MultiredditsFragment), and this dialog doesn't need to survive a
// config change. Membership is a one-shot snapshot; no live-updating list while it's open.
object MultiredditPickerDialog {

    fun show(
        context: Context,
        scope: CoroutineScope,
        layoutInflater: LayoutInflater,
        target: String,
        type: MultiredditMemberType,
        getMultireddits: suspend () -> List<MultiredditWithMembers>,
        addMember: (multiId: Long) -> Unit,
        removeMember: (multiId: Long) -> Unit,
        createMultireddit: (name: String) -> Unit
    ) {
        scope.launch {
            val multireddits = getMultireddits()
            if (multireddits.isEmpty()) {
                showCreateDialog(context, layoutInflater, createMultireddit)
                return@launch
            }

            val names = multireddits.map { it.multireddit.name }.toTypedArray()
            val checked = BooleanArray(multireddits.size) { i ->
                multireddits[i].members.any {
                    it.targetName.equals(target, ignoreCase = true) &&
                        MultiredditMemberType.fromValue(it.type) == type
                }
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.add_to_multireddit)
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    val multiId = multireddits[which].multireddit.id
                    if (isChecked) addMember(multiId) else removeMember(multiId)
                }
                .setPositiveButton(R.string.dialog_ok, null)
                .setNeutralButton(R.string.multireddit_create) { _, _ ->
                    showCreateDialog(context, layoutInflater, createMultireddit)
                }
                .show()
        }
    }

    private fun showCreateDialog(
        context: Context,
        layoutInflater: LayoutInflater,
        createMultireddit: (name: String) -> Unit
    ) {
        val createBinding = DialogAddProfileBinding.inflate(layoutInflater)
        createBinding.inputName.hint = context.getString(R.string.dialog_create_multireddit_hint)

        MaterialAlertDialogBuilder(context)
            .setView(createBinding.root)
            .setTitle(R.string.dialog_create_multireddit_title)
            .setPositiveButton(R.string.dialog_create_multireddit_button) { _, _ ->
                // Ignore, overridden below
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val name = createBinding.inputName.text().toString()
                    if (name.isBlank()) {
                        createBinding.inputName.error = context.getString(R.string.profile_blank_error)
                    } else {
                        createMultireddit(name)
                        dismiss()
                    }
                }
            }
    }
}
