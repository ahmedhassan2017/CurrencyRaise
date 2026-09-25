package com.example.currencyraise.domain.model

import java.math.BigDecimal

data class SavingsBalance(
    val usd: BigDecimal = BigDecimal.ZERO,
    val egp: BigDecimal = BigDecimal.ZERO,
    val goldGrams: BigDecimal = BigDecimal.ZERO,
    val goldKarat: GoldKarat = GoldKarat.K21,
) {
    init {
        require(usd.signum() >= 0 && egp.signum() >= 0 && goldGrams.signum() >= 0)
    }

    companion object {
        val EMPTY = SavingsBalance()
    }
}

data class SavingsValuation(
    val totalEgp: BigDecimal,
    val usdValueEgp: BigDecimal,
    val goldValueEgp: BigDecimal,
)

fun SavingsBalance.valueAt(
    exchangeRate: ExchangeRate?,
    goldRate: GoldRate? = null,
): SavingsValuation? {
    val usdValue = if (usd.signum() == 0) {
        BigDecimal.ZERO
    } else {
        val rate = exchangeRate ?: return null
        if (rate.baseCurrency != "USD" || rate.quoteCurrency != "EGP") return null
        usd.multiply(rate.buyRate)
    }
    val goldValue = if (goldGrams.signum() == 0) {
        BigDecimal.ZERO
    } else {
        val rate = goldRate ?: return null
        if (rate.karat != goldKarat) return null
        goldGrams.multiply(rate.buyRate)
    }
    return SavingsValuation(
        totalEgp = egp.add(usdValue).add(goldValue),
        usdValueEgp = usdValue,
        goldValueEgp = goldValue,
    )
}

enum class SavingsWriteResult { SAVED, STORAGE_FAILURE }
