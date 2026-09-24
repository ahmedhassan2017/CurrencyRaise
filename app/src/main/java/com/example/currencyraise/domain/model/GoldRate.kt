package com.example.currencyraise.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

enum class GoldKarat(val ta3weemSlug: String) {
    K24("GOLD24K"),
    K21("GOLD21K"),
    K18("GOLD18K"),
}

data class GoldRate(
    val karat: GoldKarat,
    val buyRate: BigDecimal,
    val sellRate: BigDecimal,
    val sourceName: String,
    val sourceUrl: String,
    val sourceDisplayedAt: LocalDateTime?,
    val fetchedAt: Instant,
)

sealed interface GoldFetchResult {
    data class Success(val rate: GoldRate) : GoldFetchResult
    data class Failure(val error: RateFetchError) : GoldFetchResult
}

sealed interface GoldRefreshOutcome {
    data class Success(
        val rate: GoldRate,
        val change: RateChange,
        val previousRate: GoldRate? = null,
    ) : GoldRefreshOutcome
    data class Cached(val rate: GoldRate) : GoldRefreshOutcome
    data class Failure(val error: RefreshError) : GoldRefreshOutcome
    data object AlreadyRefreshing : GoldRefreshOutcome
}
