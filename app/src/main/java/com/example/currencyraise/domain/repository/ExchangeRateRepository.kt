package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RefreshOutcome
import com.example.currencyraise.domain.model.RateObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant

interface ExchangeRateRepository {
    /** Emits null only for an empty cache; a read failure throws StorageReadException. */
    fun observeLatestUsdEgpRate(): Flow<ExchangeRate?>
    /** Successful observed quotes, ordered by device check time, retained locally for up to 8 days. */
    fun observeUsdEgpHistory(): Flow<List<RateObservation>>
    /** Most recent provider request, including failed requests. */
    fun observeLastAttempt(): Flow<Instant?> = flowOf(null)
    /** Positive minimumAge skips HTTP when the cache was checked recently; manual calls use zero. */
    suspend fun refreshUsdEgpRate(minimumAge: Duration = Duration.ZERO): RefreshOutcome
}
