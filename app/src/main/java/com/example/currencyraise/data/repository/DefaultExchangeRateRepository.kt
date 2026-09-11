package com.example.currencyraise.data.repository

import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.remote.RateRemoteSource
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.domain.model.RateFetchResult
import com.example.currencyraise.domain.model.RefreshError
import com.example.currencyraise.domain.model.RefreshOutcome
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.ExchangeRateRepository
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import com.example.currencyraise.data.remote.retryAfterDeadline
import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.repository.SyncStateRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex

internal class DefaultExchangeRateRepository(
    private val source: RateRemoteSource,
    private val cache: RateCache,
    private val syncState: SyncStateRepository,
    private val clock: Clock,
) : ExchangeRateRepository {
    private val refreshMutex = Mutex()
    private var memoryDeadline: Instant? = null

    override fun observeLatestUsdEgpRate() = cache.observe().catch { error ->
        if (error is IOException) throw StorageReadException(error)
        throw error
    }

    override suspend fun refreshUsdEgpRate(minimumAge: Duration): RefreshOutcome {
        require(!minimumAge.isNegative)
        if (!refreshMutex.tryLock()) return RefreshOutcome.AlreadyRefreshing
        try {
            val previous = try {
                cache.read()
            } catch (_: IOException) {
                return RefreshOutcome.Failure(RefreshError.StorageRead)
            }
            val now = clock.instant()
            if (previous != null && minimumAge > Duration.ZERO) {
                val age = Duration.between(previous.fetchedAt, now)
                if (!age.isNegative && age < minimumAge) return RefreshOutcome.Cached(previous)
            }
            val deadline = try {
                listOfNotNull(memoryDeadline, syncState.retryNotBefore()).maxOrNull()
            } catch (_: IOException) {
                return RefreshOutcome.Failure(RefreshError.StorageRead)
            }
            if (deadline != null && now < deadline) return RefreshOutcome.Failure(RefreshError.Deferred(deadline))
            val fetched = when (val result = source.fetchLatest()) {
                is RateFetchResult.Failure -> {
                    val error = result.error
                    val until = if (error is RateFetchError.Http)
                        retryAfterDeadline(error.retryAfter, clock.instant()) else null
                    if (until != null) {
                        memoryDeadline = until
                        try {
                            syncState.deferRequestsUntil(until)
                        } catch (_: IOException) {
                            return RefreshOutcome.Failure(RefreshError.StorageWrite)
                        }
                        return RefreshOutcome.Failure(RefreshError.Deferred(until))
                    }
                    return RefreshOutcome.Failure(RefreshError.Fetch(error))
                }
                is RateFetchResult.Success -> result.rate
            }
            // The lock spans read/fetch/save; device clock changes must not block refresh.
            val change = when {
                previous == null -> RateChange.FIRST_QUOTE
                previous.baseCurrency != fetched.baseCurrency ||
                    previous.quoteCurrency != fetched.quoteCurrency ||
                    previous.sourceId != fetched.sourceId ||
                    previous.quoteKind != fetched.quoteKind ||
                    previous.buyRate.compareTo(fetched.buyRate) != 0 ||
                    previous.sellRate.compareTo(fetched.sellRate) != 0 -> RateChange.CHANGED
                else -> RateChange.UNCHANGED
            }
            try {
                // Persist even when prices are unchanged so last successful fetch time advances.
                cache.save(fetched)
            } catch (_: IOException) {
                return RefreshOutcome.Failure(RefreshError.StorageWrite)
            }
            return RefreshOutcome.Success(fetched, change)
        } finally {
            // Cancellation propagates to the source; it never becomes a normal failure result.
            refreshMutex.unlock()
        }
    }
}
