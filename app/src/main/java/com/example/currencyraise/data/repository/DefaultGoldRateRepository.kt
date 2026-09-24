package com.example.currencyraise.data.repository

import com.example.currencyraise.data.local.GoldRateCache
import com.example.currencyraise.data.remote.GoldRemoteSource
import com.example.currencyraise.data.remote.retryAfterDeadline
import com.example.currencyraise.domain.model.GoldFetchResult
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.model.RefreshError
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.GoldRateRepository
import com.example.currencyraise.domain.repository.SyncStateRepository
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex

internal class DefaultGoldRateRepository(
    private val source: GoldRemoteSource,
    private val cache: GoldRateCache,
    private val syncState: SyncStateRepository,
    private val clock: Clock,
) : GoldRateRepository {
    private val refreshLocks = GoldKarat.entries.associateWith { Mutex() }
    private var memoryDeadline: Instant? = null

    override fun observeLatest(karat: GoldKarat) = cache.observe(karat).catch { error ->
        if (error is IOException) throw StorageReadException(error)
        throw error
    }

    override suspend fun refresh(karat: GoldKarat, minimumAge: Duration): GoldRefreshOutcome {
        require(!minimumAge.isNegative)
        val lock = refreshLocks.getValue(karat)
        if (!lock.tryLock()) return GoldRefreshOutcome.AlreadyRefreshing
        try {
            val previous = try {
                cache.read(karat)
            } catch (_: IOException) {
                return GoldRefreshOutcome.Failure(RefreshError.StorageRead)
            }
            val now = clock.instant()
            if (previous != null && minimumAge > Duration.ZERO) {
                val age = Duration.between(previous.fetchedAt, now)
                if (!age.isNegative && age < minimumAge) return GoldRefreshOutcome.Cached(previous)
            }
            val deadline = try {
                listOfNotNull(memoryDeadline, syncState.retryNotBefore()).maxOrNull()
            } catch (_: IOException) {
                return GoldRefreshOutcome.Failure(RefreshError.StorageRead)
            }
            if (deadline != null && now < deadline) {
                return GoldRefreshOutcome.Failure(RefreshError.Deferred(deadline))
            }
            val fetched = when (val result = source.fetchLatest(karat)) {
                is GoldFetchResult.Success -> result.rate.also {
                    if (it.karat != karat) {
                        return GoldRefreshOutcome.Failure(
                            RefreshError.Fetch(RateFetchError.InvalidResponse)
                        )
                    }
                }
                is GoldFetchResult.Failure -> {
                    val error = result.error
                    val until = if (error is RateFetchError.Http) {
                        retryAfterDeadline(error.retryAfter, clock.instant())
                    } else null
                    if (until != null) {
                        memoryDeadline = until
                        try {
                            syncState.deferRequestsUntil(until)
                        } catch (_: IOException) {
                            return GoldRefreshOutcome.Failure(RefreshError.StorageWrite)
                        }
                        return GoldRefreshOutcome.Failure(RefreshError.Deferred(until))
                    }
                    return GoldRefreshOutcome.Failure(RefreshError.Fetch(error))
                }
            }
            val change = when {
                previous == null || previous.sourceName != fetched.sourceName -> RateChange.FIRST_QUOTE
                previous.buyRate.compareTo(fetched.buyRate) != 0 ||
                    previous.sellRate.compareTo(fetched.sellRate) != 0 -> RateChange.CHANGED
                else -> RateChange.UNCHANGED
            }
            try {
                cache.save(fetched)
            } catch (_: IOException) {
                return GoldRefreshOutcome.Failure(RefreshError.StorageWrite)
            }
            return GoldRefreshOutcome.Success(fetched, change, previous)
        } finally {
            lock.unlock()
        }
    }
}
