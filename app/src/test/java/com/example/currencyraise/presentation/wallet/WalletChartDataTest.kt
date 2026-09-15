package com.example.currencyraise.presentation.wallet

import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.presentation.home.ChartPeriod
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletChartDataTest {
    private val now = Instant.parse("2026-09-16T12:00:00Z")
    private fun point(hoursAgo: Long, buy: String) = RateObservation(
        now.minusSeconds(hoursAgo * 3600),
        buy.toBigDecimal(),
        buy.toBigDecimal().add(BigDecimal("0.10")),
    )

    @Test fun chartShowsRateDrivenEgpValueForMixedSavings() {
        val balance = SavingsBalance(usd = BigDecimal("100"), egp = BigDecimal("5000"))
        val data = walletChartData(
            balance,
            listOf(point(12, "50"), point(0, "52")),
            ChartPeriod.DAILY,
            now,
        )

        assertEquals(0, BigDecimal("10000").compareTo(data.points.first().totalEgp))
        assertEquals(0, BigDecimal("10200").compareTo(data.points.last().totalEgp))
        assertEquals(0, BigDecimal("200").compareTo(data.change))
    }

    @Test fun egpOnlySavingsRemainFlatWhenTheRateChanges() {
        val data = walletChartData(
            SavingsBalance(egp = BigDecimal("5000")),
            listOf(point(12, "50"), point(0, "52")),
            ChartPeriod.DAILY,
            now,
        )

        assertEquals(0, data.change?.signum())
        assertTrue(data.maximum > data.minimum)
    }

    @Test fun oneObservationDoesNotClaimAChange() {
        val data = walletChartData(
            SavingsBalance(usd = BigDecimal.ONE),
            listOf(point(0, "52")),
            ChartPeriod.DAILY,
            now,
        )

        assertNull(data.change)
    }
}
