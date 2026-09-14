package com.example.currencyraise.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RateNotificationPublisherTest {
    @Test fun bothBanksShowCorrectIndependentArrowsInCollapsedAndExpandedText() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val context = appContext.createConfigurationContext(Configuration(appContext.resources.configuration).apply {
            setLocale(Locale.US)
        })
        val publisher = RateNotificationPublisher(context)
        val baseline = ExchangeRate("USD", "EGP", BigDecimal("51.20"), BigDecimal("51.40"),
            "banque_misr", "Banque Misr", "https://www.banquemisr.com/", QuoteKind.CASH,
            null, null, Instant.parse("2026-09-11T10:00:00Z"))
        val cases = listOf(
            listOf("51.21", "51.41", "↑ Up 0.01", "↑ Up 0.01"),
            listOf("51.19", "51.39", "↓ Down 0.01", "↓ Down 0.01"),
            listOf("51.21", "51.39", "↑ Up 0.01", "↓ Down 0.01"),
            listOf("51.2000", "51.41", "No change", "↑ Up 0.01"),
            listOf("51.21", "51.4000", "↑ Up 0.01", "No change"),
            listOf("51.200000001", "51.40", "↑ Up 0.000000001", "No change"),
        )
        for (bank in listOf(baseline, baseline.copy(sourceId = "cib_ta3weem", sourceName = "CIB via Ta3weem", quoteKind = QuoteKind.BANK_RATE))) {
            for ((buy, sell, buyDirection, sellDirection) in cases) {
                val rate = bank.copy(buyRate = buy.toBigDecimal(), sellRate = sell.toBigDecimal(), fetchedAt = bank.fetchedAt.plusSeconds(3600))
                val notification = publisher.buildNotification(rate, bank)
                assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains(bank.sourceName))
                for (key in listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)) {
                    val text = notification.extras.getCharSequence(key).toString()
                    val buyLine = text.substringAfter("Bank buys:").substringBefore("Bank sells:")
                    val sellLine = text.substringAfter("Bank sells:")
                    assertTrue(text, buyLine.contains("($buyDirection)"))
                    assertTrue(text, sellLine.contains("($sellDirection)"))
                    if (buyDirection == "No change") assertFalse(buyLine.contains("↑") || buyLine.contains("↓"))
                    if (sellDirection == "No change") assertFalse(sellLine.contains("↑") || sellLine.contains("↓"))
                }
            }
        }
    }

    @Test fun missingComparisonAndDifferentBankProduceNoArrows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val publisher = RateNotificationPublisher(context)
        val rate = ExchangeRate("USD", "EGP", BigDecimal("51.27"), BigDecimal("51.37"),
            "banque_misr", "Banque Misr", "https://www.banquemisr.com/", QuoteKind.CASH,
            null, null, Instant.parse("2026-09-11T10:00:00Z"))
        for (notification in listOf(publisher.buildNotification(rate), publisher.buildTestNotification(rate),
            publisher.buildNotification(rate, rate.copy(sourceId = "cib_ta3weem")))) {
            val text = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            assertFalse(text.contains("↑") || text.contains("↓"))
            assertFalse(text.contains("No change"))
        }
    }

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
