package com.example.currencyraise.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.currencyraise.domain.model.NotificationAccess
import com.example.currencyraise.domain.model.NotificationAccessStatus

// Shared with the notification publisher. Reading status does not create a channel.
internal const val RATE_CHANNEL_ID = "exchange_rate_updates"

internal class SystemNotificationAccess(private val context: Context) {
    fun read(): NotificationAccess {
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        var channelEnabled = true
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel(RATE_CHANNEL_ID)
            channelEnabled = channel?.importance != NotificationManager.IMPORTANCE_NONE
            if (Build.VERSION.SDK_INT >= 28 && channel?.group != null) {
                channelEnabled = channelEnabled &&
                    manager.getNotificationChannelGroup(channel.group)?.isBlocked != true
            }
        }
        return NotificationAccess(
            permissionGranted, NotificationManagerCompat.from(context).areNotificationsEnabled(), channelEnabled,
        )
    }

    fun settingsIntent(status: NotificationAccessStatus): Intent = when {
        Build.VERSION.SDK_INT >= 26 && status == NotificationAccessStatus.CHANNEL_BLOCKED ->
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, RATE_CHANNEL_ID)
        Build.VERSION.SDK_INT >= 26 ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else -> appDetailsIntent()
    }

    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
}
