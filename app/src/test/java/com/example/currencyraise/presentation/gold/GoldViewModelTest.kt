package com.example.currencyraise.presentation.gold

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.domain.repository.GoldRateRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoldViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val clock = MutableClock(Instant.parse("2026-09-25T00:35:00Z"))
    private val repository = FakeGoldRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() { owner.clear(); Dispatchers.resetMain() }

    @Test fun doesNotPollUntilGoldTabBecomesActive() = runTest(dispatcher) {
        val viewModel = create()
        runCurrent()
        assertEquals(0, repository.calls)
        viewModel.setActive(true)
        runCurrent()
        assertEquals(1, repository.calls)
        assertEquals(rate(), viewModel.uiState.value.rate)
    }

    @Test fun foregroundCheckUsesFiveMinuteCacheFloor() = runTest(dispatcher) {
        repository.saved.getValue(GoldKarat.K21).value = rate()
        val viewModel = create()
        runCurrent()
        viewModel.setActive(true)
        runCurrent()
        assertEquals(0, repository.calls)

        clock.current = clock.current.plusSeconds(300)
        viewModel.refreshIfDue()
        runCurrent()
        assertEquals(1, repository.calls)
        assertEquals(Duration.ofMinutes(5), repository.minimumAges.single())
    }

    @Test fun switchingKaratClearsOldQuoteAndLoadsIndependentCache() = runTest(dispatcher) {
        repository.saved.getValue(GoldKarat.K21).value = rate()
        repository.saved.getValue(GoldKarat.K24).value = rate(GoldKarat.K24)
        val viewModel = create()
        runCurrent()
        assertEquals(GoldKarat.K21, viewModel.uiState.value.rate?.karat)
        viewModel.selectKarat(GoldKarat.K24)
        assertNull(viewModel.uiState.value.rate)
        assertTrue(viewModel.uiState.value.loadingCache)
        runCurrent()
        assertEquals(GoldKarat.K24, viewModel.uiState.value.rate?.karat)
        assertFalse(viewModel.uiState.value.loadingCache)
    }

    private fun create() = GoldViewModel(repository, clock, SavedStateHandle()).also {
        owner.put("gold", it)
    }

    private fun rate(
        karat: GoldKarat = GoldKarat.K21,
        fetchedAt: Instant = clock.current,
    ) = GoldRate(
        karat,
        BigDecimal("6205.00"),
        BigDecimal("6225.00"),
        "Gold Bullion EG via Ta3weem",
        "https://ta3weem.com/en/gold-prices/${karat.ta3weemSlug}",
        LocalDateTime.of(2026, 9, 25, 0, 30),
        fetchedAt,
    )

    private inner class FakeGoldRepository : GoldRateRepository {
        val saved = GoldKarat.entries.associateWith { MutableStateFlow<GoldRate?>(null) }
        val minimumAges = mutableListOf<Duration>()
        var calls = 0

        override fun observeLatest(karat: GoldKarat) = saved.getValue(karat)

        override suspend fun refresh(karat: GoldKarat, minimumAge: Duration): GoldRefreshOutcome {
            calls++
            minimumAges += minimumAge
            val latest = rate(karat)
            saved.getValue(karat).value = latest
            return GoldRefreshOutcome.Success(latest, RateChange.FIRST_QUOTE)
        }
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun instant() = current
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }
}
