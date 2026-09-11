package com.example.currencyraise.data.repository

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.local.SyncStateStore
import com.example.currencyraise.data.quote
import com.example.currencyraise.data.remote.RateRemoteSource
import com.example.currencyraise.domain.model.*
import java.io.IOException
import java.time.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RefreshThrottlingTest {
    private val now = Instant.parse("2026-09-11T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val cache = RateCache(FaultablePreferences())
    private val statePreferences = FaultablePreferences()
    private val state = SyncStateStore(statePreferences)

    @Test fun recentBackgroundCheckUsesCacheWithoutChangingTimestamp() = runTest {
        val saved = quote(fetchedAt = "2026-09-11T09:59:00Z")
        cache.save(saved)
        val repository = DefaultExchangeRateRepository({ fail("No HTTP"); RateFetchResult.Success(quote()) }, cache, state, clock)
        assertEquals(RefreshOutcome.Cached(saved), repository.refreshUsdEgpRate(Duration.ofMinutes(5)))
        assertEquals(saved, cache.read())
    }

    @Test fun manualRefreshBypassesFreshnessButNotProviderDeadline() = runTest {
        cache.save(quote())
        var calls = 0
        val repository = DefaultExchangeRateRepository({
            calls++; RateFetchResult.Failure(RateFetchError.Http(429, "120"))
        }, cache, state, clock)
        val expected = RefreshOutcome.Failure(RefreshError.Deferred(now.plusSeconds(120)))
        assertEquals(expected, repository.refreshUsdEgpRate())
        assertEquals(expected, repository.refreshUsdEgpRate())
        val reopened = DefaultExchangeRateRepository({ fail("Deadline survives repository recreation")
            RateFetchResult.Success(quote()) }, cache, state, clock)
        assertEquals(expected, reopened.refreshUsdEgpRate())
        assertEquals(RefreshOutcome.Cached(quote()), reopened.refreshUsdEgpRate(Duration.ofMillis(1)))
        assertEquals(1, calls)
    }

    @Test fun requestResumesAtDeadline() = runTest {
        state.deferRequestsUntil(now)
        var calls = 0
        val repository = DefaultExchangeRateRepository({ calls++; RateFetchResult.Success(quote()) }, cache, state, clock)
        assertTrue(repository.refreshUsdEgpRate() is RefreshOutcome.Success)
        assertEquals(1, calls)
    }

    @Test fun recentCheckGuardIsInsideSharedRefreshLock() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<RateFetchResult>()
        var calls = 0
        val repository = DefaultExchangeRateRepository({ calls++; started.complete(Unit); response.await() }, cache, state, clock)
        val manual = async { repository.refreshUsdEgpRate() }
        started.await()
        assertEquals(RefreshOutcome.AlreadyRefreshing, repository.refreshUsdEgpRate(Duration.ofMinutes(5)))
        response.complete(RateFetchResult.Success(quote()))
        manual.await()
        assertEquals(RefreshOutcome.Cached(quote()), repository.refreshUsdEgpRate(Duration.ofMinutes(5)))
        assertEquals(1, calls)
    }

    @Test fun futureOrExpiredCacheStillFetches() = runTest {
        for (time in listOf("2026-09-11T10:01:00Z", "2026-09-11T09:55:00Z")) {
            cache.save(quote(fetchedAt = time))
            val repository = DefaultExchangeRateRepository({ RateFetchResult.Success(quote()) }, cache, state, clock)
            assertTrue(repository.refreshUsdEgpRate(Duration.ofMinutes(5)) is RefreshOutcome.Success)
        }
    }

    @Test fun failedDeadlineWriteStillProtectsCurrentProcess() = runTest {
        statePreferences.writeFailure = IOException("disk full")
        var calls = 0
        val repository = DefaultExchangeRateRepository({
            calls++; RateFetchResult.Failure(RateFetchError.Http(503, "60"))
        }, cache, state, clock)
        assertEquals(RefreshOutcome.Failure(RefreshError.StorageWrite), repository.refreshUsdEgpRate())
        assertEquals(RefreshOutcome.Failure(RefreshError.Deferred(now.plusSeconds(60))), repository.refreshUsdEgpRate())
        assertEquals(1, calls)
    }

    @Test fun unreadableDeadlineDoesNotAllowRequest() = runTest {
        statePreferences.readFailure = IOException("bad state")
        val repository = DefaultExchangeRateRepository({ fail("No HTTP"); RateFetchResult.Success(quote()) }, cache, state, clock)
        assertEquals(RefreshOutcome.Failure(RefreshError.StorageRead), repository.refreshUsdEgpRate())
    }
}
