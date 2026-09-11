package com.example.currencyraise.background

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.SettingsStore
import com.example.currencyraise.data.local.SyncStateStore
import com.example.currencyraise.data.quote
import com.example.currencyraise.data.repository.DefaultSettingsRepository
import com.example.currencyraise.domain.model.*
import com.example.currencyraise.domain.repository.ExchangeRateRepository
import com.example.currencyraise.notification.RateChangeAlerts
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackgroundBehaviorTest {
    private val settings = DefaultSettingsRepository(SettingsStore(FaultablePreferences(), FaultablePreferences()))
    private val state = SyncStateStore(FaultablePreferences())
    private var posts = 0
    private var sinkFailure: RuntimeException? = null
    private val alerts = RateChangeAlerts(state) { sinkFailure?.let { throw it }; posts++; true }
    private val rates = FakeRates()
    private val runner = BackgroundRefreshRunner(settings, rates, alerts)

    @Test fun firstBackgroundResultIsBaselineEvenIfRepositoryReportsChange() = runTest {
        rates.result = RefreshOutcome.Success(quote(), RateChange.CHANGED)
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertEquals(0, posts)
        assertEquals(Duration.ofMinutes(5), rates.minimumAge)
    }

    @Test fun changedPricesNotifyOnceAndEventSurvivesHelperRecreation() = runTest {
        alerts.handle(quote(), RateChange.FIRST_QUOTE, true)
        val next = quote(buy = "51.28", fetchedAt = "2026-09-11T11:00:00Z")
        assertTrue(alerts.handle(next, RateChange.CHANGED, true))
        assertFalse(RateChangeAlerts(state) { posts++; true }.handle(next, RateChange.CHANGED, true))
        assertEquals(1, posts)
    }

    @Test fun unchangedAndDisabledQuotesAdvanceStateWithoutAlerts() = runTest {
        alerts.handle(quote(), RateChange.FIRST_QUOTE, true)
        assertFalse(alerts.handle(quote(fetchedAt = "2026-09-11T11:00:00Z"), RateChange.UNCHANGED, true))
        val next = quote(buy = "51.28", fetchedAt = "2026-09-11T12:00:00Z")
        assertFalse(alerts.handle(next, RateChange.CHANGED, false))
        assertFalse(alerts.handle(next, RateChange.CHANGED, true))
        assertEquals(0, posts)
    }

    @Test fun returningToEarlierPriceAfterManualChangeIsStillAnAlert() = runTest {
        alerts.handle(quote(), RateChange.FIRST_QUOTE, true)
        assertTrue(alerts.handle(quote(fetchedAt = "2026-09-11T12:00:00Z"), RateChange.CHANGED, true))
    }

    @Test fun disabledAutomaticChecksDoNotFetch() = runTest {
        settings.setAutomaticChecksEnabled(false)
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertEquals(0, rates.calls)
    }

    @Test fun notificationsOffStillFetches() = runTest {
        settings.setNotificationsEnabled(false)
        runner.run(0)
        assertEquals(1, rates.calls)
        assertEquals(0, posts)
    }

    @Test fun preferencesAreReadAgainAfterFetch() = runTest {
        alerts.handle(quote(), RateChange.FIRST_QUOTE, true)
        rates.result = RefreshOutcome.Success(quote(buy = "51.28"), RateChange.CHANGED)
        rates.duringRefresh = { settings.setNotificationsEnabled(false) }
        runner.run(0)
        assertEquals(0, posts)
    }

    @Test fun automaticOffDuringFetchSuppressesAlert() = runTest {
        alerts.handle(quote(), RateChange.FIRST_QUOTE, true)
        rates.result = RefreshOutcome.Success(quote(buy = "51.28"), RateChange.CHANGED)
        rates.duringRefresh = { settings.setAutomaticChecksEnabled(false) }
        runner.run(0)
        assertEquals(0, posts)
    }

    @Test fun cachedResultEstablishesBaselineWithoutPosting() = runTest {
        rates.result = RefreshOutcome.Cached(quote())
        runner.run(0)
        rates.result = RefreshOutcome.Success(quote(buy = "51.28"), RateChange.CHANGED)
        runner.run(0)
        assertEquals(1, posts)
    }

    @Test fun transientFailuresAreBoundedToThreeAttempts() = runTest {
        for (error in listOf(RateFetchError.Network, RateFetchError.Timeout, RateFetchError.Http(408),
            RateFetchError.Http(425), RateFetchError.Http(429), RateFetchError.Http(500),
            RateFetchError.Http(502), RateFetchError.Http(503), RateFetchError.Http(504))) {
            rates.result = RefreshOutcome.Failure(RefreshError.Fetch(error))
            assertEquals(BackgroundResult.RETRY, runner.run(0))
            assertEquals(BackgroundResult.RETRY, runner.run(1))
            assertEquals(BackgroundResult.FAILED, runner.run(2))
            assertEquals(BackgroundResult.FAILED, runner.run(9))
        }
    }

    @Test fun permanentFailuresDoNotRetry() = runTest {
        for (error in listOf(RefreshError.StorageRead, RefreshError.StorageWrite,
            RefreshError.Fetch(RateFetchError.InvalidResponse),
            RefreshError.Fetch(RateFetchError.Http(401)), RefreshError.Fetch(RateFetchError.Http(403)),
            RefreshError.Fetch(RateFetchError.Http(404)), RefreshError.Fetch(RateFetchError.Http(501)))) {
            rates.result = RefreshOutcome.Failure(error)
            assertEquals(BackgroundResult.FAILED, runner.run(0))
        }
    }

    @Test fun providerDeadlineWaitsForPeriodicCadenceWithoutRetryLoop() = runTest {
        rates.result = RefreshOutcome.Failure(RefreshError.Deferred(quote().fetchedAt))
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
    }

    @Test fun competingRefreshRetriesAreBounded() = runTest {
        rates.result = RefreshOutcome.AlreadyRefreshing
        assertEquals(BackgroundResult.RETRY, runner.run(0))
        assertEquals(BackgroundResult.COMPLETED, runner.run(2))
    }

    @Test fun notificationFailureDoesNotRetrySavedRate() = runTest {
        alerts.handle(quote(), RateChange.FIRST_QUOTE, true)
        rates.result = RefreshOutcome.Success(quote(buy = "51.28"), RateChange.CHANGED)
        sinkFailure = IllegalStateException("notification service unavailable")
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        assertEquals(1, rates.calls)
    }

    @Test fun failedNotificationStateWriteDoesNotRetryHttp() = runTest {
        val store = FaultablePreferences().apply { writeFailure = IOException("disk full") }
        val failedAlerts = RateChangeAlerts(SyncStateStore(store)) { fail("Must not post"); false }
        assertEquals(BackgroundResult.COMPLETED, BackgroundRefreshRunner(settings, rates, failedAlerts).run(0))
        assertEquals(1, rates.calls)
    }

    @Test fun deniedNotificationIsClaimedWithoutReplay() = runTest {
        val blocked = RateChangeAlerts(state) { false }
        blocked.handle(quote(), RateChange.FIRST_QUOTE, true)
        val next = quote(buy = "51.28")
        assertFalse(blocked.handle(next, RateChange.CHANGED, true))
        assertFalse(alerts.handle(next, RateChange.CHANGED, true))
        assertEquals(0, posts)
    }

    @Test fun cancellationIsNeverConvertedToSuccess() = runTest {
        alerts.handle(quote(), RateChange.FIRST_QUOTE, true)
        rates.result = RefreshOutcome.Success(quote(buy = "51.28"), RateChange.CHANGED)
        sinkFailure = CancellationException("worker stopped")
        try { runner.run(0); fail("Expected cancellation") } catch (_: CancellationException) { }
    }

    private class FakeRates : ExchangeRateRepository {
        var calls = 0
        var minimumAge: Duration? = null
        var result: RefreshOutcome = RefreshOutcome.Success(quote(), RateChange.FIRST_QUOTE)
        var duringRefresh: suspend () -> Unit = {}
        override fun observeLatestUsdEgpRate() = flowOf<ExchangeRate?>(null)
        override suspend fun refreshUsdEgpRate(minimumAge: Duration): RefreshOutcome {
            calls++
            this.minimumAge = minimumAge
            duringRefresh()
            return result
        }
    }
}
