package com.example.currencyraise.data.repository

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.GoldRateCache
import com.example.currencyraise.data.local.SyncStateStore
import com.example.currencyraise.data.remote.GoldRemoteSource
import com.example.currencyraise.domain.model.GoldFetchResult
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RateChange
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGoldRateRepositoryTest {
    private val now = Instant.parse("2026-09-25T00:35:00Z")
    private fun rate(
        karat: GoldKarat = GoldKarat.K21,
        buy: String = "6205.00",
        fetchedAt: Instant = now,
    ) = GoldRate(
        karat = karat,
        buyRate = BigDecimal(buy),
        sellRate = BigDecimal("6225.00"),
        sourceName = "Gold Bullion EG via Ta3weem",
        sourceUrl = "https://ta3weem.com/en/gold-prices/${karat.ta3weemSlug}",
        sourceDisplayedAt = LocalDateTime.of(2026, 9, 25, 0, 30),
        fetchedAt = fetchedAt,
    )

    @Test fun selectedKaratPersistsIndependently() = runTest {
        val cache = GoldRateCache(FaultablePreferences())
        val expected = rate()
        val repository = repository(cache) { GoldFetchResult.Success(expected) }
        assertNull(repository.observeLatest(GoldKarat.K21).first())
        assertEquals(
            GoldRefreshOutcome.Success(expected, RateChange.FIRST_QUOTE),
            repository.refresh(GoldKarat.K21),
        )
        assertEquals(expected, repository.observeLatest(GoldKarat.K21).first())
        assertNull(repository.observeLatest(GoldKarat.K24).first())
    }

    @Test fun fiveMinuteMinimumAgeSkipsExtraProviderRequestBelowBoundary() = runTest {
        val cache = GoldRateCache(FaultablePreferences())
        val saved = rate(fetchedAt = now.minusSeconds(299))
        cache.save(saved)
        var calls = 0
        val repository = repository(cache) { calls++; GoldFetchResult.Success(rate()) }
        assertEquals(
            GoldRefreshOutcome.Cached(saved),
            repository.refresh(GoldKarat.K21, Duration.ofMinutes(5)),
        )
        assertEquals(0, calls)
    }

    @Test fun exactlyFiveMinutesOldPerformsNewProviderCheck() = runTest {
        val cache = GoldRateCache(FaultablePreferences())
        val previous = rate(fetchedAt = now.minusSeconds(300))
        cache.save(previous)
        var calls = 0
        val latest = rate()
        val repository = repository(cache) { calls++; GoldFetchResult.Success(latest) }
        assertEquals(
            GoldRefreshOutcome.Success(latest, RateChange.UNCHANGED, previous),
            repository.refresh(GoldKarat.K21, Duration.ofMinutes(5)),
        )
        assertEquals(1, calls)
        assertEquals(now, repository.observeLastAttempt(GoldKarat.K21).first())
    }

    @Test fun cachedResultDoesNotClaimANewProviderAttempt() = runTest {
        val cache = GoldRateCache(FaultablePreferences())
        cache.save(rate(fetchedAt = now.minusSeconds(60)))
        val repository = repository(cache) { GoldFetchResult.Success(rate()) }

        assertTrue(repository.refresh(GoldKarat.K21, Duration.ofMinutes(5)) is GoldRefreshOutcome.Cached)
        assertNull(repository.observeLastAttempt(GoldKarat.K21).first())
    }

    @Test fun rateChangeUsesDecimalValueNotScale() = runTest {
        val cache = GoldRateCache(FaultablePreferences())
        val previous = rate(buy = "6205.0", fetchedAt = now.minusSeconds(600))
        cache.save(previous)
        val latest = rate(buy = "6205.00")
        val repository = repository(cache) { GoldFetchResult.Success(latest) }
        assertEquals(
            GoldRefreshOutcome.Success(latest, RateChange.UNCHANGED, previous),
            repository.refresh(GoldKarat.K21),
        )
    }

    private fun repository(cache: GoldRateCache, source: GoldRemoteSource) =
        DefaultGoldRateRepository(
            source,
            cache,
            SyncStateStore(FaultablePreferences()),
            Clock.fixed(now, ZoneOffset.UTC),
        )
}
