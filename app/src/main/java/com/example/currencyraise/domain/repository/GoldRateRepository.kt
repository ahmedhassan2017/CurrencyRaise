package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import java.time.Duration
import kotlinx.coroutines.flow.Flow

interface GoldRateRepository {
    /** Emits null only when this karat has never been cached. */
    fun observeLatest(karat: GoldKarat): Flow<GoldRate?>

    /** A positive minimum age avoids polling Ta3weem more often than intended. */
    suspend fun refresh(karat: GoldKarat, minimumAge: Duration = Duration.ZERO): GoldRefreshOutcome
}
