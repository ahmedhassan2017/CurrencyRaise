package com.example.currencyraise.background

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.GoldAlertStateStore
import com.example.currencyraise.data.local.SettingsStore
import com.example.currencyraise.data.repository.DefaultSettingsRepository
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.domain.repository.GoldRateRepository
import com.example.currencyraise.notification.GoldChangeAlerts
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldBackgroundRefreshRunnerTest {
    private val settings = DefaultSettingsRepository(
        SettingsStore(FaultablePreferences(), FaultablePreferences())
    )
    private val rates = FakeGoldRates()
    private val posted = mutableListOf<GoldKarat>()
    private val alerts = GoldChangeAlerts(GoldAlertStateStore(FaultablePreferences())) {
        posted += it.rate.karat
        true
    }
    private val runner = GoldBackgroundRefreshRunner(settings, rates, alerts)

    @Test fun checksAllKaratsAndAlertsOnlyAfterEachBaselineChanges() = runTest {
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertEquals(GoldKarat.entries.toSet(), rates.calls.toSet())
        assertEquals(List(GoldKarat.entries.size) { Duration.ofMinutes(5) }, rates.minimumAges)
        assertTrue(posted.isEmpty())

        rates.changed = true
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertEquals(GoldKarat.entries.toSet(), posted.toSet())
    }

    @Test fun automaticChecksOffSkipsEveryKarat() = runTest {
        settings.setAutomaticChecksEnabled(false)
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertTrue(rates.calls.isEmpty())
    }

    private class FakeGoldRates : GoldRateRepository {
        val calls = mutableListOf<GoldKarat>()
        val minimumAges = mutableListOf<Duration>()
        var changed = false

        override fun observeLatest(karat: GoldKarat) = flowOf<GoldRate?>(null)

        override suspend fun refresh(karat: GoldKarat, minimumAge: Duration): GoldRefreshOutcome {
            calls += karat
            minimumAges += minimumAge
            val previous = rate(karat, changed = false)
            val current = rate(karat, changed)
            return GoldRefreshOutcome.Success(
                current,
                if (changed) RateChange.CHANGED else RateChange.FIRST_QUOTE,
                previous.takeIf { changed },
            )
        }

        private fun rate(karat: GoldKarat, changed: Boolean) = GoldRate(
            karat = karat,
            buyRate = BigDecimal(if (changed) "6206" else "6205"),
            sellRate = BigDecimal("6225"),
            sourceName = "Ta3weem",
            sourceUrl = "https://ta3weem.com/en/gold-prices/${karat.ta3weemSlug}",
            sourceDisplayedAt = LocalDateTime.of(2026, 9, 25, 12, 0),
            fetchedAt = Instant.parse(if (changed) "2026-09-25T10:05:00Z" else "2026-09-25T10:00:00Z"),
        )
    }
}
