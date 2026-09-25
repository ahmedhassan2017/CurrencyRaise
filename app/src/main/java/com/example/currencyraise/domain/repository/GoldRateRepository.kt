package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RateObservation
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GoldRateRepository {
    /** Emits null only when this karat has never been cached. */
    fun observeLatest(karat: GoldKarat): Flow<GoldRate?>

    /** Successful local observations for the selected karat, oldest first. */
    fun observeHistory(karat: GoldKarat): Flow<List<RateObservation>>

    /** Most recent provider request for this karat, including failed requests. */
    fun observeLastAttempt(karat: GoldKarat): Flow<Instant?> = flowOf(null)

    /** A positive minimum age avoids polling Ta3weem more often than intended. */
    suspend fun refresh(karat: GoldKarat, minimumAge: Duration = Duration.ZERO): GoldRefreshOutcome
}
