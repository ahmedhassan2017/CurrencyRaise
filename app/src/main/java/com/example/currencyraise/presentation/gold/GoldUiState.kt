package com.example.currencyraise.presentation.gold

import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.presentation.home.HomeError
import java.time.Duration
import java.time.Instant

internal val GOLD_CHECK_INTERVAL: Duration = Duration.ofMinutes(5)

data class GoldUiState(
    val karat: GoldKarat = GoldKarat.K21,
    val rate: GoldRate? = null,
    val loadingCache: Boolean = true,
    val refreshing: Boolean = false,
    val rateReadFailed: Boolean = false,
    val refreshError: HomeError? = null,
    val lastChange: RateChange? = null,
    val alreadyRefreshing: Boolean = false,
    val now: Instant = Instant.EPOCH,
) {
    val freshness: GoldFreshness
        get() {
            val saved = rate ?: return GoldFreshness.UNKNOWN
            val age = Duration.between(saved.fetchedAt, now)
            if (age.isNegative) return GoldFreshness.UNKNOWN
            return if (age >= GOLD_CHECK_INTERVAL) GoldFreshness.CHECK_DUE
            else GoldFreshness.RECENT_CHECK
        }
}

enum class GoldFreshness { RECENT_CHECK, CHECK_DUE, UNKNOWN }
