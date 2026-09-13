package com.example.currencyraise.presentation.home

import com.example.currencyraise.domain.model.RateObservation
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RateChartDataTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private fun point(hoursAgo: Long, buy: String = "51.27", sell: String = "51.37") =
        RateObservation(now.minusSeconds(hoursAgo * 3600), buy.toBigDecimal(), sell.toBigDecimal())

    @Test fun dailyAndWeeklyIncludeBoundariesButExcludeOldAndFuturePoints() {
        val points = listOf(point(169), point(168), point(25), point(24), point(0), point(-1))
        assertEquals(listOf(point(24), point(0)), rateChartData(points, ChartPeriod.DAILY, now).points)
        assertEquals(listOf(point(168), point(25), point(24), point(0)), rateChartData(points, ChartPeriod.WEEKLY, now).points)
    }

    @Test fun emptyAndSinglePointDoNotClaimChanges() {
        val empty = rateChartData(emptyList(), ChartPeriod.DAILY, now)
        assertTrue(empty.points.isEmpty())
        assertNull(empty.buyChange)
        assertNull(empty.sellChange)
        val single = rateChartData(listOf(point(0)), ChartPeriod.DAILY, now)
        assertNull(single.buyChange)
        assertEquals(1f, single.x(now))
    }

    @Test fun changesUseExactDecimalsForEachSideAndOnlyObservedCoverage() {
        val data = rateChartData(listOf(point(1, "51.270000001", "51.38"), point(3, "51.27", "51.39")), ChartPeriod.DAILY, now)
        assertEquals(0, data.buyChange!!.compareTo(BigDecimal("0.000000001")))
        assertEquals(0, data.sellChange!!.compareTo(BigDecimal("-0.01")))
        assertEquals(point(3, "51.27", "51.39"), data.points.first())
        assertEquals(2, data.points.size) // No fabricated midnight or current-time quote.
    }

    @Test fun flatEqualRatesHaveFiniteCoordinatesWithHeadroom() {
        val data = rateChartData(listOf(point(24, "51.27", "51.27"), point(0, "51.27", "51.27")), ChartPeriod.DAILY, now)
        assertEquals(0f, data.x(data.start))
        assertEquals(1f, data.x(data.end))
        assertEquals(0.5f, data.y(BigDecimal("51.27")), 0.001f)
        assertEquals(0, data.buyChange!!.signum())
        assertTrue(data.maximum > data.minimum)
    }

    @Test fun duplicateTimesAreOnePositionAndLatestValueWins() {
        val data = rateChartData(listOf(point(2), point(0), point(0, "51.30")), ChartPeriod.DAILY, now)
        assertEquals(2, data.points.size)
        assertEquals(BigDecimal("51.30"), data.points.last().buyRate)
    }
}
