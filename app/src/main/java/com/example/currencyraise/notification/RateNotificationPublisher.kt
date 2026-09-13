package com.example.currencyraise.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import com.example.currencyraise.MainActivity
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.NotificationAccessStatus
import com.example.currencyraise.domain.model.RateMovement
import com.example.currencyraise.domain.model.RateDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RateNotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationSink {
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(RATE_CHANNEL_ID, context.getString(R.string.rate_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.rate_channel_description)
                }
            )
        }
    }

    override fun publish(alert: RateAlert): Boolean =
        post(notificationId(alert.rate), buildNotification(alert.rate, alert.previousRate))

    internal fun notificationId(rate: ExchangeRate): Int =
        if (rate.sourceId == "cib_ta3weem") CIB_NOTIFICATION_ID else RATE_NOTIFICATION_ID

    internal fun publishTest(rate: ExchangeRate): Boolean =
        post(TEST_NOTIFICATION_ID, buildTestNotification(rate))

    private fun post(notificationId: Int, notification: android.app.Notification): Boolean {
        createChannel()
        if (SystemNotificationAccess(context).read().status != NotificationAccessStatus.ALLOWED) return false
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) { false } // Permission can change between check and post.
    }

    internal fun buildNotification(rate: ExchangeRate, previousRate: ExchangeRate? = null): android.app.Notification =
        buildNotification(RateAlert(rate, previousRate), context.getString(R.string.rate_notification_title, rate.sourceName))

    internal fun buildTestNotification(rate: ExchangeRate): android.app.Notification =
        buildNotification(RateAlert(rate), context.getString(R.string.debug_test_notification_title))

    private fun buildNotification(alert: RateAlert, title: String): android.app.Notification {
        val rate = alert.rate
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.US
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 9
        }
        fun price(value: java.math.BigDecimal, movement: RateMovement?): String {
            val direction = when (movement?.direction) {
                RateDirection.UP -> context.getString(R.string.notification_direction_up, formatter.format(movement.difference.abs()))
                RateDirection.DOWN -> context.getString(R.string.notification_direction_down, formatter.format(movement.difference.abs()))
                RateDirection.UNCHANGED -> context.getString(R.string.rate_direction_unchanged)
                null -> return formatter.format(value)
            }
            return context.getString(R.string.notification_price_direction, formatter.format(value), direction)
        }
        val buy = price(rate.buyRate, alert.buyMovement)
        val sell = price(rate.sellRate, alert.sellMovement)
        val body = context.getString(R.string.rate_notification_body, buy, sell)
        val expandedBody = if (alert.buyMovement != null && alert.sellMovement != null)
            context.getString(R.string.rate_notification_expanded, buy, sell) else body
        val bank = if (rate.sourceId == "cib_ta3weem") Bank.CIB else Bank.BANQUE_MISR
        val openHome = PendingIntent.getActivity(context, notificationId(rate),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_BANK, bank.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, RATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rate_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setContentIntent(openHome)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        const val RATE_NOTIFICATION_ID = 1001
        const val TEST_NOTIFICATION_ID = 1002
        const val CIB_NOTIFICATION_ID = 1003
    }
}
