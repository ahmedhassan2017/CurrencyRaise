package com.example.currencyraise.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

/** Prices are quote-currency units per one base-currency unit, from the bank's perspective. */
data class ExchangeRate(
    val baseCurrency: String,
    val quoteCurrency: String,
    val buyRate: BigDecimal,
    val sellRate: BigDecimal,
    val sourceId: String,
    val sourceName: String,
    val sourceUrl: String,
    val quoteKind: QuoteKind,
    // The source supplies a wall-clock value without a timezone. Never treat it as UTC.
    val sourceDisplayedAt: LocalDateTime?,
    val sourceQuoteId: String?,
    val fetchedAt: Instant,
) {
    init {
        require(baseCurrency.matches(Regex("[A-Z]{3}")))
        require(quoteCurrency.matches(Regex("[A-Z]{3}")))
        require(buyRate.signum() > 0 && sellRate >= buyRate)
        require(sourceId.isNotBlank() && sourceName.isNotBlank())
    }
}

enum class QuoteKind { CASH }
