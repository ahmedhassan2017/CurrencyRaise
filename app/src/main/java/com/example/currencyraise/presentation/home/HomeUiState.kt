package com.example.currencyraise.presentation.home

import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RateChange
import java.time.Duration
import java.time.Instant

data class HomeUiState(
    val rate: ExchangeRate? = null,
    val settings: AppSettings? = null,
    val loadingCache: Boolean = true,
    val refreshing: Boolean = false,
    val rateReadFailed: Boolean = false,
    val settingsReadFailed: Boolean = false,
    val refreshError: HomeError? = null,
    val lastChange: RateChange? = null,
    val alreadyRefreshing: Boolean = false,
    val now: Instant = Instant.EPOCH,
) {
    val freshness: Freshness
        get() {
            val saved = rate ?: return Freshness.UNKNOWN
            val age = Duration.between(saved.fetchedAt, now)
            if (age.isNegative) return Freshness.UNKNOWN
            val interval = settings?.updateInterval?.hours ?: 1
            return if (age >= Duration.ofHours(interval.toLong())) Freshness.CHECK_DUE else Freshness.RECENT_CHECK
        }
}

enum class Freshness { RECENT_CHECK, CHECK_DUE, UNKNOWN }
enum class HomeError { DEFERRED, NETWORK, TIMEOUT, PROVIDER, RATE_LIMITED, STORAGE_READ, STORAGE_WRITE }
