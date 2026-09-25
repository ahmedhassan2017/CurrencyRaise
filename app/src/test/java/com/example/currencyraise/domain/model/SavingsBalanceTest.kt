package com.example.currencyraise.domain.model

import com.example.currencyraise.data.quote
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavingsBalanceTest {
    @Test fun valuesMixedSavingsUsingTheHonestSideOfEachBankRate() {
        val balance = SavingsBalance(usd = BigDecimal("100"), egp = BigDecimal("5200"))
        val valuation = balance.valueAt(quote(buy = "50", sell = "52"))

        assertEquals(0, BigDecimal("10200").compareTo(valuation?.totalEgp))
    }

    @Test fun rejectsAnUnexpectedCurrencyPair() {
        assertNull(SavingsBalance(usd = BigDecimal.ONE).valueAt(quote().copy(baseCurrency = "EUR")))
    }

    @Test fun valuesGoldWithTheDealerBuyRateAndAddsEgp() {
        val balance = SavingsBalance(
            egp = BigDecimal("5000"),
            goldGrams = BigDecimal("2.5"),
            goldKarat = GoldKarat.K21,
        )

        val valuation = balance.valueAt(null, goldRate(GoldKarat.K21, "4000"))

        assertEquals(0, BigDecimal("15000").compareTo(valuation?.totalEgp))
        assertEquals(0, BigDecimal("10000.0").compareTo(valuation?.goldValueEgp))
    }

    @Test fun requiresOnlyRatesUsedByTheSavedAssets() {
        assertEquals(
            0,
            BigDecimal("5000").compareTo(SavingsBalance(egp = BigDecimal("5000")).valueAt(null)?.totalEgp),
        )
        assertNull(
            SavingsBalance(goldGrams = BigDecimal.ONE, goldKarat = GoldKarat.K21)
                .valueAt(null, goldRate(GoldKarat.K24, "5000")),
        )
    }

    private fun goldRate(karat: GoldKarat, buy: String) = GoldRate(
        karat = karat,
        buyRate = buy.toBigDecimal(),
        sellRate = buy.toBigDecimal().add(BigDecimal("100")),
        sourceName = "Ta3weem",
        sourceUrl = "https://ta3weem.com/",
        sourceDisplayedAt = null,
        fetchedAt = Instant.parse("2026-09-25T10:00:00Z"),
    )
}
