package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RefreshOutcome
import kotlinx.coroutines.flow.Flow
import java.time.Duration

interface ExchangeRateRepository {
    /** Emits null only for an empty cache; a read failure throws StorageReadException. */
    fun observeLatestUsdEgpRate(): Flow<ExchangeRate?>
    /** Positive minimumAge skips HTTP when the cache was checked recently; manual calls use zero. */
    suspend fun refreshUsdEgpRate(minimumAge: Duration = Duration.ZERO): RefreshOutcome
}
