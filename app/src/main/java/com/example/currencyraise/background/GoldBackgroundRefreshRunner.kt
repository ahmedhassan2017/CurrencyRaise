package com.example.currencyraise.background

import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.model.RefreshError
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.GoldRateRepository
import com.example.currencyraise.domain.repository.SettingsRepository
import java.time.Duration
import kotlinx.coroutines.flow.first

internal class GoldBackgroundRefreshRunner(
    private val settings: SettingsRepository,
    private val rates: GoldRateRepository,
) : BackgroundRateRunner {
    override suspend fun run(attempt: Int): BackgroundResult {
        val initial = try {
            settings.observeSettings().first()
        } catch (_: StorageReadException) {
            return BackgroundResult.FAILED
        }
        if (!initial.automaticChecksEnabled) return BackgroundResult.COMPLETED

        val results = GoldKarat.entries.map { karat -> refresh(karat, attempt) }
        return when {
            BackgroundResult.RETRY in results -> BackgroundResult.RETRY
            BackgroundResult.FAILED in results -> BackgroundResult.FAILED
            else -> BackgroundResult.COMPLETED
        }
    }

    private suspend fun refresh(karat: GoldKarat, attempt: Int): BackgroundResult =
        when (val result = rates.refresh(karat, Duration.ofMinutes(5))) {
            is GoldRefreshOutcome.Success, is GoldRefreshOutcome.Cached -> BackgroundResult.COMPLETED
            GoldRefreshOutcome.AlreadyRefreshing ->
                if (attempt < MAX_RETRIES) BackgroundResult.RETRY else BackgroundResult.COMPLETED
            is GoldRefreshOutcome.Failure -> when (val error = result.error) {
                is RefreshError.Deferred -> BackgroundResult.COMPLETED
                is RefreshError.Fetch -> if (error.error.isTransient() && attempt < MAX_RETRIES) {
                    BackgroundResult.RETRY
                } else {
                    BackgroundResult.FAILED
                }
                RefreshError.StorageRead, RefreshError.StorageWrite -> BackgroundResult.FAILED
            }
        }

    private fun RateFetchError.isTransient(): Boolean = when (this) {
        RateFetchError.Network, RateFetchError.Timeout -> true
        is RateFetchError.Http -> statusCode in setOf(408, 425, 429, 500, 502, 503, 504)
        RateFetchError.InvalidResponse -> false
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}
