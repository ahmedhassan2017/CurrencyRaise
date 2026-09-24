package com.example.currencyraise.notification

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.GoldAlertStateStore
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.domain.model.RateDirection
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldChangeAlertsTest {
    private val posted = mutableListOf<GoldRateAlert>()
    private val alerts = GoldChangeAlerts(GoldAlertStateStore(FaultablePreferences())) {
        posted += it
        true
    }

    @Test fun eachKaratGetsAnIndependentSilentBaselineThenOneChangeAlert() = runTest {
        for (karat in GoldKarat.entries) {
            assertFalse(alerts.handle(rate(karat), RateChange.FIRST_QUOTE, enabled = true))
        }
        assertTrue(posted.isEmpty())

        for (karat in GoldKarat.entries) {
            val previous = rate(karat)
            val changed = rate(
                karat,
                buy = previous.buyRate.add(BigDecimal.ONE).toPlainString(),
                sell = previous.sellRate.subtract(BigDecimal.ONE).toPlainString(),
                fetchedAt = previous.fetchedAt.plusSeconds(300),
            )
            assertTrue(alerts.handle(changed, RateChange.CHANGED, enabled = true, previous))
            assertFalse(alerts.handle(changed, RateChange.CHANGED, enabled = true, previous))
        }

        assertEquals(GoldKarat.entries.toSet(), posted.map { it.rate.karat }.toSet())
        posted.forEach {
            assertEquals(RateDirection.UP, it.buyMovement?.direction)
            assertEquals(RateDirection.DOWN, it.sellMovement?.direction)
        }
    }

    @Test fun disabledAlertIsClaimedAndCannotReplayLater() = runTest {
        val previous = rate(GoldKarat.K21)
        alerts.handle(previous, RateChange.FIRST_QUOTE, enabled = true)
        val changed = rate(GoldKarat.K21, buy = "6206", fetchedAt = previous.fetchedAt.plusSeconds(300))
        assertFalse(alerts.handle(changed, RateChange.CHANGED, enabled = false, previous))
        assertFalse(alerts.handle(changed, RateChange.CHANGED, enabled = true, previous))
        assertTrue(posted.isEmpty())
    }

    private fun rate(
        karat: GoldKarat,
        buy: String = "6205",
        sell: String = "6225",
        fetchedAt: Instant = Instant.parse("2026-09-25T10:00:00Z"),
    ) = GoldRate(
        karat = karat,
        buyRate = BigDecimal(buy),
        sellRate = BigDecimal(sell),
        sourceName = "Ta3weem",
        sourceUrl = "https://ta3weem.com/en/gold-prices/${karat.ta3weemSlug}",
        sourceDisplayedAt = LocalDateTime.of(2026, 9, 25, 12, 0),
        fetchedAt = fetchedAt,
    )
}
