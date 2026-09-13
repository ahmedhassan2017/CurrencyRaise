package com.example.currencyraise.background

import com.example.currencyraise.domain.model.*
import com.example.currencyraise.domain.repository.ExchangeRateRepository
import com.example.currencyraise.domain.repository.SettingsRepository
import com.example.currencyraise.notification.RateChangeAlerts
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal enum class BackgroundResult { COMPLETED, RETRY, FAILED }

internal class BackgroundRefreshRunner(
    private val settings: SettingsRepository,
    private val rates: ExchangeRateRepository,
    private val alerts: RateChangeAlerts,
) {
    suspend fun run(attempt: Int): BackgroundResult {
        val initial = try { settings.observeSettings().first() }
            catch (_: StorageReadException) { return BackgroundResult.FAILED }
        if (!initial.automaticChecksEnabled) return BackgroundResult.COMPLETED
        return when (val result = rates.refreshUsdEgpRate(Duration.ofMinutes(5))) {
            is RefreshOutcome.Success -> finish(result.rate, result.change)
            is RefreshOutcome.Cached -> finish(result.rate, RateChange.UNCHANGED)
            RefreshOutcome.AlreadyRefreshing ->
                if (attempt < MAX_RETRIES) BackgroundResult.RETRY else BackgroundResult.COMPLETED
            is RefreshOutcome.Failure -> when (val error = result.error) {
                is RefreshError.Deferred -> BackgroundResult.COMPLETED
                is RefreshError.Fetch -> if (error.error.isTransient() && attempt < MAX_RETRIES)
                    BackgroundResult.RETRY else BackgroundResult.FAILED
                RefreshError.StorageRead, RefreshError.StorageWrite -> BackgroundResult.FAILED
            }
        }
    }

    private suspend fun finish(rate: ExchangeRate, change: RateChange): BackgroundResult {
        // A saved quote is already success. Alert failure must never trigger another HTTP request.
        try {
            val latest = settings.observeSettings().first()
            alerts.handle(rate, change, latest.automaticChecksEnabled && latest.notificationsEnabled)
        } catch (error: CancellationException) {
            throw error
        } catch (_: StorageReadException) {
            return BackgroundResult.COMPLETED
        } catch (_: IOException) {
            return BackgroundResult.COMPLETED
        } catch (_: RuntimeException) {
            // Android's notification service may fail after the quote was safely persisted.
            return BackgroundResult.COMPLETED
        }
        return BackgroundResult.COMPLETED
    }

    private fun RateFetchError.isTransient(): Boolean = when (this) {
        RateFetchError.Network, RateFetchError.Timeout -> true
        is RateFetchError.Http -> statusCode in setOf(408, 425, 429, 500, 502, 503, 504)
        RateFetchError.InvalidResponse -> false
    }

    private companion object { const val MAX_RETRIES = 2 }
}
