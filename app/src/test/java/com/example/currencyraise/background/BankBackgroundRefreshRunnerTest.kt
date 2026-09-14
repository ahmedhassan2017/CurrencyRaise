package com.example.currencyraise.background

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.local.SettingsStore
import com.example.currencyraise.data.local.SyncStateStore
import com.example.currencyraise.data.quote
import com.example.currencyraise.data.repository.DefaultExchangeRateRepository
import com.example.currencyraise.data.repository.DefaultSettingsRepository
import com.example.currencyraise.domain.model.*
import com.example.currencyraise.notification.RateChangeAlerts
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BankBackgroundRefreshRunnerTest {
    private val settings = DefaultSettingsRepository(SettingsStore(FaultablePreferences(), FaultablePreferences()))
    private val posted = mutableListOf<ExchangeRate>()
    private val misr = Provider(quote())
    private val cib = Provider(quote().copy(sourceId = "cib_ta3weem", sourceName = "CIB via Ta3weem",
        quoteKind = QuoteKind.BANK_RATE))
    private val runner = BankBackgroundRefreshRunner(listOf(misr.runner, cib.runner))

    private inner class Provider(var rate: ExchangeRate) {
        var result: RateFetchResult = RateFetchResult.Success(rate)
        var calls = 0
        val state = SyncStateStore(FaultablePreferences())
        val cache = RateCache(FaultablePreferences())
        // Deliberately older quote times force each test refresh to hit its source.
        val repository = DefaultExchangeRateRepository(
            { calls++; result }, cache, state,
            Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC),
        )
        val runner = BackgroundRefreshRunner(settings, repository, RateChangeAlerts(state) { posted.add(it.rate); true })
        fun change() {
            rate = rate.copy(buyRate = rate.buyRate + "0.01".toBigDecimal(), fetchedAt = rate.fetchedAt.plusSeconds(60))
            result = RateFetchResult.Success(rate)
        }
    }

    @Test fun eachBankHasItsOwnSilentBaselineAndChangeAlerts() = runTest {
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertTrue(posted.isEmpty())
        misr.change()
        cib.change()
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertEquals(setOf("banque_misr", "cib_ta3weem"), posted.map { it.sourceId }.toSet())
        assertEquals(misr.rate, misr.cache.read())
        assertEquals(cib.rate, cib.cache.read())
        runner.run(0)
        assertEquals(2, posted.size)
    }

    @Test fun oneBanksFailureDoesNotPreventOtherBankFromSavingAndAlerting() = runTest {
        runner.run(0)
        val savedMisr = misr.cache.read()
        misr.result = RateFetchResult.Failure(RateFetchError.Network)
        cib.change()
        assertEquals(BackgroundResult.RETRY, runner.run(0))
        assertEquals(savedMisr, misr.cache.read())
        assertEquals(cib.rate, cib.cache.read())
        assertEquals(listOf("cib_ta3weem"), posted.map { it.sourceId })
        assertEquals(BackgroundResult.FAILED, runner.run(2))
    }

    @Test fun cibFirstSuccessStaysSilentAfterMisrAlreadyEstablishedBaseline() = runTest {
        cib.result = RateFetchResult.Failure(RateFetchError.InvalidResponse)
        runner.run(0)
        cib.result = RateFetchResult.Success(cib.rate)
        misr.change()
        runner.run(0)
        assertEquals(listOf("banque_misr"), posted.map { it.sourceId })
        cib.change()
        runner.run(0)
        assertEquals(listOf("banque_misr", "cib_ta3weem"), posted.map { it.sourceId })
    }

    @Test fun retryAfterIsIsolatedToItsProvider() = runTest {
        cib.result = RateFetchResult.Failure(RateFetchError.Http(429, "120"))
        runner.run(0)
        assertNotNull(cib.state.retryNotBefore())
        assertNull(misr.state.retryNotBefore())
        runner.run(0)
        assertEquals(1, cib.calls)
        assertEquals(2, misr.calls)
        assertNotNull(misr.repository.observeLatestUsdEgpRate().first())
    }

    @Test fun disabledAutomaticChecksSkipBothProviders() = runTest {
        settings.setAutomaticChecksEnabled(false)
        runner.run(0)
        assertEquals(0, cib.calls)
        assertEquals(0, misr.calls)
    }

    @Test fun disabledNotificationsStillUpdateBothBanks() = runTest {
        runner.run(0)
        settings.setNotificationsEnabled(false)
        misr.change()
        cib.change()
        runner.run(0)
        assertEquals(misr.rate, misr.cache.read())
        assertEquals(cib.rate, cib.cache.read())
        assertTrue(posted.isEmpty())
    }
}
