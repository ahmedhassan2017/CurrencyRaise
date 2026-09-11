package com.example.currencyraise.presentation.home

import androidx.lifecycle.ViewModelStore
import com.example.currencyraise.data.quote
import com.example.currencyraise.domain.model.*
import com.example.currencyraise.domain.repository.ExchangeRateRepository
import com.example.currencyraise.domain.repository.SettingsRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val clock = MutableClock(Instant.parse("2026-09-11T10:30:00Z"))
    private val rates = FakeRates()
    private val settings = FakeSettings()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owner.clear(); Dispatchers.resetMain() }
    private fun create(): HomeViewModel = HomeViewModel(rates, settings, clock).also { owner.put("home", it) }

    @Test fun recentCacheDisplaysWithoutNetworkRequest() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote())
        val vm = create()
        runCurrent()
        assertEquals(quote(), vm.uiState.value.rate)
        assertFalse(vm.uiState.value.loadingCache)
        assertEquals(0, rates.calls)
    }

    @Test fun emptyCacheFetchesAndDisplaysObservedSavedQuote() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        assertEquals(1, rates.calls)
        assertEquals(quote(), vm.uiState.value.rate)
        assertEquals(RateChange.FIRST_QUOTE, vm.uiState.value.lastChange)
    }

    @Test fun staleCacheRemainsVisibleDuringFetchAndFailure() = runTest(dispatcher) {
        val old = quote(fetchedAt = "2026-09-11T08:00:00Z")
        rates.saved.value = Result.success(old)
        val response = CompletableDeferred<RefreshOutcome>()
        rates.action = { response.await() }
        val vm = create()
        runCurrent()
        assertTrue(vm.uiState.value.refreshing)
        assertEquals(old, vm.uiState.value.rate)
        response.complete(RefreshOutcome.Failure(RefreshError.Fetch(RateFetchError.Network)))
        runCurrent()
        assertEquals(old, vm.uiState.value.rate)
        assertFalse(vm.uiState.value.refreshing)
        assertEquals(HomeError.NETWORK, vm.uiState.value.refreshError)
    }

    @Test fun selectedIntervalControlsInitialFreshnessIncludingBoundary() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote(fetchedAt = "2026-09-11T09:00:00Z"))
        settings.saved.value = Result.success(AppSettings(updateInterval = UpdateInterval.TWO_HOURS))
        val vm = create()
        runCurrent()
        assertEquals(0, rates.calls)
        clock.current = Instant.parse("2026-09-11T11:00:00Z")
        vm.updateDisplayTime()
        assertEquals(Freshness.CHECK_DUE, vm.uiState.value.freshness)
        assertEquals(0, rates.calls) // Display ticks never perform network polling.
        owner.clear()
        create()
        runCurrent()
        assertEquals(1, rates.calls)
    }

    @Test fun futureDatedCacheTriggersOneCheckWithoutClaimingFreshness() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote(fetchedAt = "2026-09-12T10:00:00Z"))
        rates.action = { RefreshOutcome.Failure(RefreshError.Fetch(RateFetchError.Timeout)) }
        val vm = create()
        runCurrent()
        assertEquals(1, rates.calls)
        assertEquals(Freshness.UNKNOWN, vm.uiState.value.freshness)
        assertEquals(HomeError.TIMEOUT, vm.uiState.value.refreshError)
    }

    @Test fun rapidManualTapsDoNotLaunchDuplicateRefreshes() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote())
        val pending = CompletableDeferred<RefreshOutcome>()
        rates.action = { pending.await() }
        val vm = create()
        runCurrent()
        vm.refresh()
        vm.refresh()
        vm.refresh()
        runCurrent()
        assertEquals(1, rates.calls)
        pending.complete(RefreshOutcome.Success(quote(), RateChange.UNCHANGED))
        runCurrent()
        assertEquals(RateChange.UNCHANGED, vm.uiState.value.lastChange)
        assertFalse(vm.uiState.value.refreshing)
    }

    @Test fun backgroundStyleUpdatesReachExistingHomeWithoutFetch() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote())
        val vm = create()
        runCurrent()
        val changed = quote(buy = "51.30", fetchedAt = "2026-09-11T10:31:00Z")
        rates.saved.value = Result.success(changed)
        runCurrent()
        assertEquals(changed, vm.uiState.value.rate)
        assertEquals(0, rates.calls)
    }

    @Test fun observationFailureKeepsQuoteAndManualRetryResubscribes() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote())
        val vm = create()
        runCurrent()
        rates.saved.value = Result.failure(StorageReadException(IOException("read failure")))
        runCurrent()
        assertEquals(quote(), vm.uiState.value.rate)
        assertTrue(vm.uiState.value.rateReadFailed)
        val recovered = quote(buy = "51.30")
        rates.saved.value = Result.success(recovered)
        rates.action = { RefreshOutcome.Success(recovered, RateChange.CHANGED) }
        vm.refresh()
        runCurrent()
        assertEquals(recovered, vm.uiState.value.rate)
        assertFalse(vm.uiState.value.rateReadFailed)
    }

    @Test fun initialStorageFailureDoesNotFetchOrEraseAndRetryCanRecover() = runTest(dispatcher) {
        rates.saved.value = Result.failure(StorageReadException(IOException("unreadable")))
        val vm = create()
        runCurrent()
        assertTrue(vm.uiState.value.rateReadFailed)
        assertEquals(0, rates.calls)
        rates.saved.value = Result.success(null)
        vm.refresh()
        runCurrent()
        assertEquals(quote(), vm.uiState.value.rate)
        assertFalse(vm.uiState.value.rateReadFailed)
        assertEquals(1, rates.calls)
    }

    @Test fun settingsFailureIsExplicitAndRetryReconnects() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote())
        settings.saved.value = Result.failure(StorageReadException(IOException("settings")))
        val vm = create()
        runCurrent()
        assertTrue(vm.uiState.value.settingsReadFailed)
        assertNull(vm.uiState.value.settings)
        settings.saved.value = Result.success(AppSettings(updateInterval = UpdateInterval.FOUR_HOURS))
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.settingsReadFailed)
        assertEquals(UpdateInterval.FOUR_HOURS, vm.uiState.value.settings?.updateInterval)
    }

    @Test fun repositoryBusyOutcomeDoesNotLeaveSpinnerStuck() = runTest(dispatcher) {
        rates.saved.value = Result.success(quote())
        rates.action = { RefreshOutcome.AlreadyRefreshing }
        val vm = create()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertTrue(vm.uiState.value.alreadyRefreshing)
        rates.saved.value = Result.success(quote(buy = "51.30"))
        runCurrent()
        assertFalse(vm.uiState.value.alreadyRefreshing)
        assertFalse(vm.uiState.value.refreshing)
        assertEquals(quote(buy = "51.30"), vm.uiState.value.rate)
    }

    @Test fun clearingViewModelCancelsInFlightWork() = runTest(dispatcher) {
        val pending = CompletableDeferred<RefreshOutcome>()
        rates.action = { pending.await() }
        val vm = create()
        runCurrent()
        assertTrue(vm.uiState.value.refreshing)
        owner.clear()
        runCurrent()
        assertFalse(vm.uiState.value.refreshing)
        assertNull(vm.uiState.value.refreshError)
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun instant() = current
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }

    private class FakeRates : ExchangeRateRepository {
        val saved = MutableStateFlow<Result<ExchangeRate?>>(Result.success(null))
        var calls = 0
        var action: suspend () -> RefreshOutcome = {
            saved.value = Result.success(quote())
            RefreshOutcome.Success(quote(), RateChange.FIRST_QUOTE)
        }
        override fun observeLatestUsdEgpRate() = saved.map { it.getOrThrow() }
        override suspend fun refreshUsdEgpRate(): RefreshOutcome { calls++; return action() }
    }

    private class FakeSettings : SettingsRepository {
        val saved = MutableStateFlow(Result.success(AppSettings()))
        override fun observeSettings() = saved.map { it.getOrThrow() }
        override suspend fun setUpdateInterval(interval: UpdateInterval) = SettingsWriteResult.SAVED
        override suspend fun setAutomaticChecksEnabled(enabled: Boolean) = SettingsWriteResult.SAVED
        override suspend fun setNotificationsEnabled(enabled: Boolean) = SettingsWriteResult.SAVED
        override suspend fun markNotificationPermissionAsked() = SettingsWriteResult.SAVED
    }
}
