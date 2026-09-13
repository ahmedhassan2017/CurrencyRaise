package com.example.currencyraise.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RateNotificationPublisherTest {
    @Test fun createsChannelAndBuildsImmutableAppOwnedTapActionWithoutPosting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val publisher = RateNotificationPublisher(context)
        publisher.createChannel()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(RATE_CHANNEL_ID)
            assertNotNull(channel)
            assertEquals("Exchange Rate Updates", channel.name.toString())
        }
        val rate = ExchangeRate("USD", "EGP", BigDecimal("51.27"), BigDecimal("51.37"),
            "banque_misr", "Banque Misr", "https://www.banquemisr.com/", QuoteKind.CASH,
            null, null, Instant.parse("2026-09-11T10:00:00Z"))
        val notification = publisher.buildNotification(rate)
        val cibRate = rate.copy(sourceId = "cib_ta3weem", sourceName = "CIB via Ta3weem")
        val cibNotification = publisher.buildNotification(cibRate)
        assertNotEquals(publisher.notificationId(rate), publisher.notificationId(cibRate))
        assertNotEquals(RateNotificationPublisher.TEST_NOTIFICATION_ID, publisher.notificationId(cibRate))
        assertNotEquals(notification.contentIntent, cibNotification.contentIntent)
        assertTrue(cibNotification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("CIB via Ta3weem"))
        assertEquals(context.packageName, notification.contentIntent.creatorPackage)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(notification.contentIntent.isImmutable)
        if (Build.VERSION.SDK_INT >= 26) assertEquals(RATE_CHANNEL_ID, notification.channelId)
        assertTrue(notification.extras.getCharSequence("android.text").toString().contains("EGP"))
        val testNotification = publisher.buildTestNotification(rate)
        assertEquals("TEST · Currency Raise notification",
            testNotification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNotEquals(RateNotificationPublisher.RATE_NOTIFICATION_ID, RateNotificationPublisher.TEST_NOTIFICATION_ID)
    }
}
