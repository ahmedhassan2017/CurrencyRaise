package com.example.currencyraise.domain.model

import java.math.BigDecimal

enum class RateDirection { UP, DOWN, UNCHANGED }

/** Decimal subtraction shared by rate cards and other presentations of a price change. */
data class RateMovement(val difference: BigDecimal) {
    val direction: RateDirection
        get() = when (difference.signum()) {
            1 -> RateDirection.UP
            -1 -> RateDirection.DOWN
            else -> RateDirection.UNCHANGED
        }

    companion object {
        fun between(previous: BigDecimal, current: BigDecimal) = RateMovement(current - previous)
    }
}
