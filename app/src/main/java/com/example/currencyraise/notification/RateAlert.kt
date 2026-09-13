package com.example.currencyraise.notification

import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RateMovement

/** Immutable snapshot of the refresh that triggered an alert; never re-read live cache here. */
internal data class RateAlert(val rate: ExchangeRate, val previousRate: ExchangeRate? = null) {
    private val comparablePrevious = previousRate?.takeIf {
        it.sourceId == rate.sourceId && it.baseCurrency == rate.baseCurrency &&
            it.quoteCurrency == rate.quoteCurrency && it.quoteKind == rate.quoteKind
    }
    val buyMovement: RateMovement? = comparablePrevious?.let { RateMovement.between(it.buyRate, rate.buyRate) }
    val sellMovement: RateMovement? = comparablePrevious?.let { RateMovement.between(it.sellRate, rate.sellRate) }
}
