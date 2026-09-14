package com.example.currencyraise.acceptance

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.currencyraise.data.mapper.toExchangeRate
import com.example.currencyraise.data.remote.BanqueMisrParser
import com.example.currencyraise.domain.model.NotificationAccessStatus
import com.example.currencyraise.notification.RATE_CHANNEL_ID
import com.example.currencyraise.notification.RateNotificationPublisher
import com.example.currencyraise.notification.SystemNotificationAccess
import java.time.Instant
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Run on a fresh releaseSmoke install. Only this disposable installation is mutated. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationAccessDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun isolatedOnly() {
        assumeTrue(context.packageName == "com.example.currencyraise.smoke")
    }

    private fun quote() = instrumentation.context.assets.open("banque-misr/usd-cash.html")
        .bufferedReader().use { BanqueMisrParser().parse(it.readText()) }
        .toExchangeRate(Instant.parse("2026-09-11T10:00:00Z"))

    @Test fun a_deniedRuntimePermissionDoesNotPost() {
        isolatedOnly()
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        assertEquals("Use a fresh releaseSmoke install for permission acceptance",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS))
        assertEquals(NotificationAccessStatus.PERMISSION_NEEDED, SystemNotificationAccess(context).read().status)
        assertFalse(RateNotificationPublisher(context).publish(com.example.currencyraise.notification.RateAlert(quote())))
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test fun b_blockedRateChannelDoesNotPost() {
        isolatedOnly()
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant com.example.currencyraise.smoke android.permission.POST_NOTIFICATIONS"
            ).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RATE_CHANNEL_ID, "Exchange Rate Updates",
            NotificationManager.IMPORTANCE_NONE))
        assertEquals(NotificationAccessStatus.CHANNEL_BLOCKED, SystemNotificationAccess(context).read().status)
        assertFalse(RateNotificationPublisher(context).publish(com.example.currencyraise.notification.RateAlert(quote())))
        assertTrue(manager.activeNotifications.isEmpty())
        // Channel blocking cannot be reversed by an app. Connected tests remove this disposable
        // installation afterward. Do not revoke a runtime permission inside its own instrumentation
        // process: Android may kill that process.
    }
}
