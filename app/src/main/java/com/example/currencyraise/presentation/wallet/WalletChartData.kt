package com.example.currencyraise.presentation.wallet

import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.presentation.home.ChartPeriod
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant

internal data class WalletValuePoint(
    val observedAt: Instant,
    val totalEgp: BigDecimal,
)

internal data class WalletChartData(
    val start: Instant,
    val end: Instant,
    val points: List<WalletValuePoint>,
    val minimum: BigDecimal,
    val maximum: BigDecimal,
) {
    val change: BigDecimal?
        get() = points.takeIf { it.size >= 2 }?.let { it.last().totalEgp - it.first().totalEgp }

    fun x(at: Instant): Float = (Duration.between(start, at).toMillis().toDouble() /
        Duration.between(start, end).toMillis()).toFloat().coerceIn(0f, 1f)

    fun y(value: BigDecimal): Float = BigDecimal.ONE.subtract(
        value.subtract(minimum).divide(maximum.subtract(minimum), MathContext.DECIMAL64),
    ).toFloat().coerceIn(0f, 1f)
}

internal fun walletChartData(
    balance: SavingsBalance,
    bankHistory: List<RateObservation>,
    goldHistory: List<RateObservation>,
    period: ChartPeriod,
    now: Instant,
): WalletChartData {
    val start = now.minus(period.duration)
    val needsBank = balance.usd.signum() > 0
    val needsGold = balance.goldGrams.signum() > 0
    val bankRates = bankHistory.sortedBy { it.observedAt }
    val goldRates = goldHistory.sortedBy { it.observedAt }
    val timestamps = when {
        !needsBank && !needsGold -> listOf(now)
        needsBank && needsGold -> (bankRates + goldRates).map(RateObservation::observedAt)
        needsBank -> bankRates.map(RateObservation::observedAt)
        else -> goldRates.map(RateObservation::observedAt)
    }.filter { it >= start && it <= now }.distinct().sorted()
    val points = timestamps.mapNotNull { observedAt ->
        val bankRate = if (needsBank) {
            bankRates.lastOrNull { it.observedAt <= observedAt }?.buyRate ?: return@mapNotNull null
        } else {
            BigDecimal.ZERO
        }
        val goldRate = if (needsGold) {
            goldRates.lastOrNull { it.observedAt <= observedAt }?.buyRate ?: return@mapNotNull null
        } else {
            BigDecimal.ZERO
        }
        WalletValuePoint(
            observedAt = observedAt,
            totalEgp = balance.egp
                .add(balance.usd.multiply(bankRate))
                .add(balance.goldGrams.multiply(goldRate)),
        )
    }
    val low = points.minOfOrNull { it.totalEgp } ?: BigDecimal.ZERO
    val high = points.maxOfOrNull { it.totalEgp } ?: BigDecimal.ONE
    val padding = high.subtract(low).multiply(BigDecimal("0.1")).max(BigDecimal("0.01"))
    return WalletChartData(
        start = start,
        end = now,
        points = points,
        minimum = low.subtract(padding).max(BigDecimal.ZERO),
        maximum = high.add(padding),
    )
}
