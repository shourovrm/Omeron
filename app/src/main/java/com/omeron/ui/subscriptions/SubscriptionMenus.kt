package com.omeron.ui.subscriptions

import android.content.Context
import android.view.LayoutInflater
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omeron.R
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.ui.common.dialog.MultiredditPickerDialog
import kotlinx.coroutines.CoroutineScope

/**
 * The action menus for a community, a followed user or a multireddit. The drawer (long-press) and
 * the manage pages (three-dot button) both open these, so the entries and confirmations match.
 *
 * Hide is only offered where the caller lists shown items alone; the manage pages have an eye
 * button for it instead.
 */
class SubscriptionMenus(
    private val context: Context,
    private val scope: CoroutineScope,
    private val layoutInflater: LayoutInflater,
    private val fragmentManager: FragmentManager,
    private val viewModel: SubscriptionsViewModel
) {

    fun showCommunityMenu(name: String, offerHide: Boolean) {
        val entries = mutableListOf<MenuEntry>()
        if (offerHide) {
            entries += MenuEntry(R.string.manage_hide) { viewModel.setSubscriptionHidden(name, true) }
        }
        entries += MenuEntry(R.string.add_to_multireddit) { showMultiredditPicker(name) }
        entries += MenuEntry(R.string.subreddit_button_unsubscribe) { confirmUnsubscribe(name) }
        showMenu(name, entries)
    }

    fun showUserMenu(name: String) {
        showMenu(name, listOf(MenuEntry(R.string.unfollow) { viewModel.unfollowUser(name) }))
    }

    fun showMultiredditMenu(multiredditId: Long, name: String, offerHide: Boolean) {
        val entries = mutableListOf<MenuEntry>()
        if (offerHide) {
            entries += MenuEntry(R.string.manage_hide) {
                viewModel.setMultiredditHidden(multiredditId, true)
            }
        }
        entries += MenuEntry(R.string.multireddit_menu_edit) {
            MultiredditEditDialogFragment.show(fragmentManager, multiredditId)
        }
        entries += MenuEntry(R.string.multireddit_menu_delete) { confirmDelete(multiredditId) }
        showMenu(name, entries)
    }

    private fun showMenu(title: String, entries: List<MenuEntry>) {
        val labels = entries.map { context.getString(it.labelRes) }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(labels) { _, which -> entries[which].action() }
            .show()
    }

    private fun showMultiredditPicker(name: String) {
        MultiredditPickerDialog.show(
            context = context,
            scope = scope,
            layoutInflater = layoutInflater,
            target = name,
            type = MultiredditMemberType.SUBREDDIT,
            getMultireddits = { viewModel.getMultiredditsSnapshot() },
            addMember = { multiId -> viewModel.addTargetToMultireddit(multiId, name) },
            removeMember = { multiId -> viewModel.removeTargetFromMultireddit(multiId, name) },
            createMultireddit = { multiredditName ->
                viewModel.createMultiredditWithTarget(multiredditName, name)
            }
        )
    }

    private fun confirmUnsubscribe(name: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(name)
            .setMessage(context.getString(R.string.dialog_unsubscribe_message, name))
            .setPositiveButton(R.string.dialog_yes) { _, _ -> viewModel.unsubscribe(name) }
            .setNegativeButton(R.string.dialog_no) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun confirmDelete(multiredditId: Long) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_delete_multireddit_title)
            .setMessage(R.string.dialog_delete_multireddit_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> viewModel.deleteMultireddit(multiredditId) }
            .setNegativeButton(R.string.dialog_no) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private class MenuEntry(val labelRes: Int, val action: () -> Unit)
}
