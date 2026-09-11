package com.example.currencyraise.data.repository

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.quote
import com.example.currencyraise.data.remote.RateRemoteSource
import com.example.currencyraise.domain.model.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DefaultExchangeRateRepositoryTest {
    @Test fun firstRefreshPersistsAndReportsFirstQuote() = runTest {
        val cache = RateCache(FaultablePreferences())
        val rate = quote()
        val repository = DefaultExchangeRateRepository({ RateFetchResult.Success(rate) }, cache)
        assertNull(repository.observeLatestUsdEgpRate().first())
        assertEquals(RefreshOutcome.Success(rate, RateChange.FIRST_QUOTE), repository.refreshUsdEgpRate())
        assertEquals(rate, repository.observeLatestUsdEgpRate().first())
    }

    @Test fun scaleOnlyDifferenceIsUnchangedButAdvancesFetchTime() = runTest {
        val cache = RateCache(FaultablePreferences())
        cache.save(quote())
        val latest = quote("51.27", "51.37", "2026-09-11T11:00:00Z")
        val repository = DefaultExchangeRateRepository({ RateFetchResult.Success(latest) }, cache)
        assertEquals(RefreshOutcome.Success(latest, RateChange.UNCHANGED), repository.refreshUsdEgpRate())
        assertEquals(latest, cache.read())
    }

    @Test fun changeOnEitherSideIsDetected() = runTest {
        for (latest in listOf(quote(buy = "51.28"), quote(sell = "51.38"))) {
            val cache = RateCache(FaultablePreferences())
            cache.save(quote(fetchedAt = "2026-09-11T09:00:00Z"))
            val repository = DefaultExchangeRateRepository({ RateFetchResult.Success(latest) }, cache)
            assertEquals(RefreshOutcome.Success(latest, RateChange.CHANGED), repository.refreshUsdEgpRate())
        }
    }

    @Test fun providerFailuresPreserveLastValidSnapshot() = runTest {
        val cache = RateCache(FaultablePreferences())
        val saved = quote()
        cache.save(saved)
        for (error in listOf(RateFetchError.Network, RateFetchError.Timeout,
            RateFetchError.InvalidResponse, RateFetchError.Http(429, "120"))) {
            val repository = DefaultExchangeRateRepository({ RateFetchResult.Failure(error) }, cache)
            assertEquals(RefreshOutcome.Failure(RefreshError.Fetch(error)), repository.refreshUsdEgpRate())
            assertEquals(saved, cache.read())
        }
    }

    @Test fun failedWriteDoesNotReportSuccessOrReplaceCache() = runTest {
        val store = FaultablePreferences()
        val cache = RateCache(store)
        val saved = quote()
        cache.save(saved)
        store.writeFailure = IOException("disk full")
        val repository = DefaultExchangeRateRepository({ RateFetchResult.Success(quote(buy = "51.30")) }, cache)
        assertEquals(RefreshOutcome.Failure(RefreshError.StorageWrite), repository.refreshUsdEgpRate())
        assertEquals(saved, cache.read())
    }

    @Test fun readFailureIsExplicitAndCanBeRetriedWithoutFetchingEarly() = runTest {
        val store = FaultablePreferences()
        val cache = RateCache(store)
        var calls = 0
        val repository = DefaultExchangeRateRepository({ calls++; RateFetchResult.Success(quote()) }, cache)
        store.readFailure = IOException("temporarily unavailable")
        assertEquals(RefreshOutcome.Failure(RefreshError.StorageRead), repository.refreshUsdEgpRate())
        assertEquals(0, calls)
        try {
            repository.observeLatestUsdEgpRate().first()
            fail("Expected storage read error")
        } catch (e: StorageReadException) {
            assertTrue(e.cause is IOException)
        }
        store.readFailure = null
        assertTrue(repository.refreshUsdEgpRate() is RefreshOutcome.Success)
        assertEquals(quote(), repository.observeLatestUsdEgpRate().first())
    }

    @Test fun deviceClockMovingBackwardDoesNotBlockNextSerializedRefresh() = runTest {
        val cache = RateCache(FaultablePreferences())
        cache.save(quote())
        val next = quote(buy = "51.28", fetchedAt = "2026-09-11T09:00:00Z")
        val repository = DefaultExchangeRateRepository({ RateFetchResult.Success(next) }, cache)
        assertEquals(RefreshOutcome.Success(next, RateChange.CHANGED), repository.refreshUsdEgpRate())
        assertEquals(next, cache.read())
    }

    @Test fun concurrentRefreshDoesNotQueueAnotherNetworkRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<RateFetchResult>()
        var calls = 0
        val source = RateRemoteSource { calls++; started.complete(Unit); response.await() }
        val repository = DefaultExchangeRateRepository(source, RateCache(FaultablePreferences()))
        val first = async { repository.refreshUsdEgpRate() }
        started.await()
        assertEquals(RefreshOutcome.AlreadyRefreshing, repository.refreshUsdEgpRate())
        response.complete(RateFetchResult.Success(quote()))
        assertTrue(first.await() is RefreshOutcome.Success)
        assertEquals(1, calls)
    }

    @Test fun cancellingRefreshPreservesCacheAndReleasesLock() = runTest {
        val cache = RateCache(FaultablePreferences())
        val saved = quote()
        cache.save(saved)
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val source = RateRemoteSource {
            if (++calls == 1) { started.complete(Unit); awaitCancellation() }
            RateFetchResult.Success(quote(fetchedAt = "2026-09-11T11:00:00Z"))
        }
        val repository = DefaultExchangeRateRepository(source, cache)
        val pending = async { repository.refreshUsdEgpRate() }
        started.await()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertEquals(saved, cache.read())
        assertTrue(repository.refreshUsdEgpRate() is RefreshOutcome.Success)
        assertEquals(2, calls)
    }

    @Test fun cancellationDuringStorageWriteIsNotConvertedToFailure() = runTest {
        val store = FaultablePreferences()
        store.writeFailure = CancellationException("cancel write")
        val repository = DefaultExchangeRateRepository({ RateFetchResult.Success(quote()) }, RateCache(store))
        try {
            repository.refreshUsdEgpRate()
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertNull(RateCache(store).read())
        }
        store.writeFailure = null
        assertTrue(repository.refreshUsdEgpRate() is RefreshOutcome.Success)
    }
}
