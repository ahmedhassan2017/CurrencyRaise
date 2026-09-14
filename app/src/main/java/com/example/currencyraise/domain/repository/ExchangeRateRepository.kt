package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RefreshOutcome
import com.example.currencyraise.domain.model.RateObservation
import kotlinx.coroutines.flow.Flow
import java.time.Duration

interface ExchangeRateRepository {
    /** Emits null only for an empty cache; a read failure throws StorageReadException. */
    fun observeLatestUsdEgpRate(): Flow<ExchangeRate?>
    /** Successful observed quotes, ordered by device check time, retained locally for up to 8 days. */
    fun observeUsdEgpHistory(): Flow<List<RateObservation>>
    /** Positive minimumAge skips HTTP when the cache was checked recently; manual calls use zero. */
    suspend fun refreshUsdEgpRate(minimumAge: Duration = Duration.ZERO): RefreshOutcome
}
