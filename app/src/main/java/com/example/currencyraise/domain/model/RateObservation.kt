package com.example.currencyraise.domain.model

import java.math.BigDecimal
import java.time.Instant

/** An actual successful check, not a bank publication instant or an inferred historical price. */
data class RateObservation(val observedAt: Instant, val buyRate: BigDecimal, val sellRate: BigDecimal) {
    init { require(buyRate.signum() > 0 && sellRate >= buyRate) }
}

fun ExchangeRate.toObservation() = RateObservation(fetchedAt, buyRate, sellRate)
