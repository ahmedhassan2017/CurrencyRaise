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
    history: List<RateObservation>,
    period: ChartPeriod,
    now: Instant,
): WalletChartData {
    val start = now.minus(period.duration)
    val points = history
        .filter { it.observedAt >= start && it.observedAt <= now }
        .associateBy { it.observedAt }
        .values
        .sortedBy { it.observedAt }
        .map { observation ->
            WalletValuePoint(
                observedAt = observation.observedAt,
                totalEgp = balance.egp.add(balance.usd.multiply(observation.buyRate)),
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
