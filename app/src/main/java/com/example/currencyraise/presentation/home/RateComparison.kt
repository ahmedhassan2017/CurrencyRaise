package com.example.currencyraise.presentation.home

import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RateMovement
import com.example.currencyraise.domain.model.RateObservation
import java.time.Instant

data class RateComparison(val buy: RateMovement, val sell: RateMovement, val previousCheck: Instant)

/** History belongs to one bank/source/pair/kind and is persisted atomically with its quote.
 * Suppress comparison while the independently collected quote and history are out of sync.
 */
internal fun compareLatestRate(rate: ExchangeRate?, history: List<RateObservation>): RateComparison? {
    if (rate == null || history.size < 2) return null
    val latest = history.last()
    if (latest.observedAt != rate.fetchedAt || latest.buyRate.compareTo(rate.buyRate) != 0 ||
        latest.sellRate.compareTo(rate.sellRate) != 0) return null
    val previous = history[history.lastIndex - 1]
    if (previous.observedAt >= latest.observedAt) return null
    return RateComparison(
        RateMovement.between(previous.buyRate, latest.buyRate),
        RateMovement.between(previous.sellRate, latest.sellRate),
        previous.observedAt,
    )
}
