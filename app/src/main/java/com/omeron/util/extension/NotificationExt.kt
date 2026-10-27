package com.omeron.util.extension

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun Context.createNotificationChannel(
    channelId: String,
    @StringRes name: Int,
    @StringRes description: Int,
    importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT
) {
    createNotificationChannel(
        channelId,
        getString(name),
        getString(description),
        importance
    )
}

fun Context.createNotificationChannel(
    channelId: String,
    name: String,
    description: String,
    importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT
) {
    val channel = NotificationChannelCompat.Builder(channelId, importance)
        .setName(name)
        .setDescription(description)
        .build()
    NotificationManagerCompat.from(this).createNotificationChannel(channel)
}

fun Context.showNotification(notificationId: Int, notification: Notification) {
    // Android 13+ drops notifications without this permission; skipping the call says so explicitly.
    val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (!canPostNotifications) return

    NotificationManagerCompat.from(this).notify(notificationId, notification)
}

fun Context.cancelNotification(notificationId: Int) {
    NotificationManagerCompat.from(this).cancel(notificationId)
}
