package com.omeron.ui.mediaviewer

import android.Manifest
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.omeron.R
import com.omeron.data.model.GalleryMedia
import com.omeron.data.worker.MediaDownloadWorker
import com.omeron.util.extension.isPermissionGranted
import com.google.android.material.snackbar.Snackbar

/**
 * Asks for the permissions a media download needs on the current Android version, then queues the
 * download. Must be created while the fragment is being constructed, because it registers
 * activity result launchers.
 */
class MediaDownloadRequester(
    private val fragment: Fragment,
    private val snackbarAnchor: () -> View
) {

    // The media to download once a permission prompt has been answered.
    private var pendingMedia: GalleryMedia? = null

    private val requestStoragePermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            downloadPendingMedia()
        } else {
            Snackbar.make(
                snackbarAnchor(),
                R.string.snackbar_permission_storage_denied_message,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Download media regardless of the result
        downloadPendingMedia()
    }

    fun request(media: GalleryMedia) {
        pendingMedia = media
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No need to request storage permission on Android 10+
            downloadPendingMedia()
        } else {
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        when {
            fragment.isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                downloadPendingMedia()
            }
            fragment.shouldShowRequestPermissionRationale(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) -> {
                Snackbar.make(
                    snackbarAnchor(),
                    R.string.snackbar_permission_storage_request_message,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.ok) {
                    requestStoragePermissionLauncher
                        .launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }.show()
            }
            else -> {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (!fragment.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS) ||
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            downloadPendingMedia()
        }
    }

    private fun downloadPendingMedia() {
        val media = pendingMedia ?: return
        val context = fragment.context ?: return

        MediaDownloadWorker.enqueueWork(context.applicationContext, media.url, media.type)

        Toast.makeText(context, R.string.toast_download_started, Toast.LENGTH_SHORT).show()
    }
}
