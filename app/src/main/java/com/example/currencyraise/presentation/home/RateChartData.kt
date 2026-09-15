package com.example.currencyraise.presentation.home

import com.example.currencyraise.domain.model.RateObservation
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant

enum class ChartPeriod(val duration: Duration) {
    DAILY(Duration.ofHours(24)), WEEKLY(Duration.ofDays(7)),
}

internal data class RateChartData(
    val start: Instant,
    val end: Instant,
    val points: List<RateObservation>,
    val minimum: BigDecimal,
    val maximum: BigDecimal,
) {
    val buyChange: BigDecimal? get() = points.takeIf { it.size >= 2 }?.let { it.last().buyRate - it.first().buyRate }
    fun x(at: Instant): Float = (Duration.between(start, at).toMillis().toDouble() /
        Duration.between(start, end).toMillis()).toFloat().coerceIn(0f, 1f)
    fun y(price: BigDecimal): Float = BigDecimal.ONE.subtract(
        price.subtract(minimum).divide(maximum.subtract(minimum), MathContext.DECIMAL64),
    ).toFloat().coerceIn(0f, 1f)
}

internal fun rateChartData(history: List<RateObservation>, period: ChartPeriod, now: Instant): RateChartData {
    val start = now.minus(period.duration)
    val points = history.filter { it.observedAt >= start && it.observedAt <= now }
        .associateBy { it.observedAt }.values.sortedBy { it.observedAt }
    val low = points.minOfOrNull { it.buyRate } ?: BigDecimal.ZERO
    val high = points.maxOfOrNull { it.buyRate } ?: BigDecimal.ONE
    // Keep the buy-rate trend readable even when the observed range is flat or very small.
    val padding = high.subtract(low).multiply(BigDecimal("0.1")).max(BigDecimal("0.01"))
    return RateChartData(start, now, points, low.subtract(padding).max(BigDecimal.ZERO), high.add(padding))
}
