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
            balance = balance,
            bankHistory = listOf(point(12, "50"), point(0, "52")),
            goldHistory = emptyList(),
            period = ChartPeriod.DAILY,
            now = now,
        )

        assertEquals(0, BigDecimal("10000").compareTo(data.points.first().totalEgp))
        assertEquals(0, BigDecimal("10200").compareTo(data.points.last().totalEgp))
        assertEquals(0, BigDecimal("200").compareTo(data.change))
    }

    @Test fun egpOnlySavingsNeedNoRateHistory() {
        val data = walletChartData(
            balance = SavingsBalance(egp = BigDecimal("5000")),
            bankHistory = listOf(point(12, "50"), point(0, "52")),
            goldHistory = emptyList(),
            period = ChartPeriod.DAILY,
            now = now,
        )

        assertEquals(1, data.points.size)
        assertEquals(0, BigDecimal("5000").compareTo(data.points.single().totalEgp))
        assertNull(data.change)
        assertTrue(data.maximum > data.minimum)
    }

    @Test fun oneObservationDoesNotClaimAChange() {
        val data = walletChartData(
            balance = SavingsBalance(usd = BigDecimal.ONE),
            bankHistory = listOf(point(0, "52")),
            goldHistory = emptyList(),
            period = ChartPeriod.DAILY,
            now = now,
        )

        assertNull(data.change)
    }

    @Test fun combinesLatestBankAndGoldBuyRatesAtEachObservation() {
        val balance = SavingsBalance(
            usd = BigDecimal("10"),
            egp = BigDecimal("1000"),
            goldGrams = BigDecimal("2"),
        )
        val data = walletChartData(
            balance = balance,
            bankHistory = listOf(point(12, "50"), point(0, "52")),
            goldHistory = listOf(point(12, "4000"), point(6, "4100")),
            period = ChartPeriod.DAILY,
            now = now,
        )

        assertEquals(0, BigDecimal("9500").compareTo(data.points.first().totalEgp))
        assertEquals(0, BigDecimal("9720").compareTo(data.points.last().totalEgp))
    }
}
