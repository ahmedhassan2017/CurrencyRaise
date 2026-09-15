package com.example.currencyraise.domain.model

import java.math.BigDecimal

data class SavingsBalance(
    val usd: BigDecimal = BigDecimal.ZERO,
    val egp: BigDecimal = BigDecimal.ZERO,
) {
    init {
        require(usd.signum() >= 0 && egp.signum() >= 0)
    }

    companion object {
        val EMPTY = SavingsBalance()
    }
}

data class SavingsValuation(
    val totalEgp: BigDecimal,
)

fun SavingsBalance.valueAt(rate: ExchangeRate): SavingsValuation? {
    if (rate.baseCurrency != "USD" || rate.quoteCurrency != "EGP") return null
    return SavingsValuation(
        totalEgp = egp.add(usd.multiply(rate.buyRate)),
    )
}

enum class SavingsWriteResult { SAVED, STORAGE_FAILURE }
