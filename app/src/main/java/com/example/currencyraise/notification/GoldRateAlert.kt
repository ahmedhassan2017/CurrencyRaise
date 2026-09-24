package com.example.currencyraise.notification

import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.RateMovement

internal data class GoldRateAlert(
    val rate: GoldRate,
    val previousRate: GoldRate? = null,
) {
    private val comparablePrevious = previousRate?.takeIf {
        it.karat == rate.karat && it.sourceName == rate.sourceName
    }
    val buyMovement: RateMovement? = comparablePrevious?.let {
        RateMovement.between(it.buyRate, rate.buyRate)
    }
    val sellMovement: RateMovement? = comparablePrevious?.let {
        RateMovement.between(it.sellRate, rate.sellRate)
    }
}
