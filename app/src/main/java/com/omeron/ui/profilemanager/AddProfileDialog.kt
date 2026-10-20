package com.omeron.ui.profilemanager

import android.app.Activity
import android.content.DialogInterface
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.R
import com.omeron.databinding.DialogAddProfileBinding
import com.omeron.util.extension.text

private const val PROFILE_NAME_MIN = 3
private const val PROFILE_NAME_MAX = 20

// Shared by the profile manager sheet and the profile switcher sheet, so both validate a name
// the same way. Returns null when the name is acceptable.
@StringRes
fun profileNameErrorRes(name: String, existingNames: List<String>): Int? {
    return when {
        name.length !in PROFILE_NAME_MIN..PROFILE_NAME_MAX -> R.string.profile_name_length_error
        existingNames.any { it.equals(name, ignoreCase = true) } -> R.string.profile_already_exists_error
        name.isBlank() -> R.string.profile_blank_error
        else -> null
    }
}

fun showAddProfileDialog(
    activity: Activity,
    existingNames: () -> List<String>,
    onCreate: (name: String) -> Unit
) {
    val profileBinding = DialogAddProfileBinding.inflate(activity.layoutInflater)
    MaterialAlertDialogBuilder(activity)
        .setView(profileBinding.root)
        .setTitle(R.string.dialog_create_profile_title)
        .setPositiveButton(R.string.dialog_create_profile_button) { _, _ ->
            // Replaced below so a rejected name keeps the dialog open.
        }
        .setNeutralButton(R.string.dialog_cancel) { dialog, _ ->
            dialog.dismiss()
        }
        .setCancelable(false)
        .show()
        .apply {
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = profileBinding.inputName.text().orEmpty()
                val errorRes = profileNameErrorRes(name, existingNames())
                if (errorRes == null) {
                    onCreate(name)
                    dismiss()
                } else {
                    profileBinding.inputName.error = activity.getString(errorRes)
                }
            }
        }
}
