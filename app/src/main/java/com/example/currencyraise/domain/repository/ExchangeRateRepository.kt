package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RefreshOutcome
import kotlinx.coroutines.flow.Flow

interface ExchangeRateRepository {
    /** Emits null only for an empty cache; a read failure throws StorageReadException. */
    fun observeLatestUsdEgpRate(): Flow<ExchangeRate?>
    suspend fun refreshUsdEgpRate(): RefreshOutcome
}
