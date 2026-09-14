package com.example.currencyraise.acceptance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.currencyraise.MainActivity
import com.example.currencyraise.data.mapper.toExchangeRate
import com.example.currencyraise.data.remote.BanqueMisrParser
import com.example.currencyraise.data.remote.CibTa3weemParser
import com.example.currencyraise.notification.RateNotificationPublisher
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class NotificationTapTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun notificationActionReturnsToHomeWhenSettingsWasOpen() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Back to Home").assertIsDisplayed()
        val html = InstrumentationRegistry.getInstrumentation().context.assets.open("banque-misr/usd-cash.html")
            .bufferedReader().use { it.readText() }
        val rate = BanqueMisrParser().parse(html).toExchangeRate(Instant.parse("2026-09-11T10:00:00Z"))
        val action = RateNotificationPublisher(compose.activity).buildNotification(rate).contentIntent
        // Exercise the real tap action without posting historical test prices as a notification.
        compose.runOnUiThread { action.send() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("USD / EGP").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Back to Home").assertDoesNotExist()
        compose.onNodeWithText("USD / EGP").assertIsDisplayed()
    }

    @Test fun cibNotificationActionOpensCibInsteadOfDefaultBank() {
        val html = InstrumentationRegistry.getInstrumentation().context.assets.open("cib-ta3weem/usd-egp.html")
            .bufferedReader().use { it.readText() }
        val rate = CibTa3weemParser().parse(html, Instant.parse("2026-09-13T12:00:00Z"))
        val action = RateNotificationPublisher(compose.activity).buildNotification(rate).contentIntent
        compose.runOnUiThread { action.send() }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("CIB") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("CIB").assertIsSelected()
        compose.onNodeWithText("CIB via Ta3weem · Bank rates").assertExists()
    }
}
