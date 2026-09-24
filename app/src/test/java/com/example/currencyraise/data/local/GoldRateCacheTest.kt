package com.example.currencyraise.data.local

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.toObservation
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GoldRateCacheTest {
    @Test fun historiesAreRecordedAtomicallyAndStaySeparateByKarat() = runTest {
        val cache = GoldRateCache(FaultablePreferences())
        val k21First = rate(GoldKarat.K21, "6205", "2026-09-25T10:00:00Z")
        val k24 = rate(GoldKarat.K24, "7091", "2026-09-25T10:01:00Z")
        val k21Second = rate(GoldKarat.K21, "6210", "2026-09-25T10:05:00Z")

        cache.save(k21First)
        cache.save(k24)
        cache.save(k21Second)

        assertEquals(listOf(k21First.toObservation(), k21Second.toObservation()), cache.readHistory(GoldKarat.K21))
        assertEquals(listOf(k24.toObservation()), cache.readHistory(GoldKarat.K24))
        assertEquals(k21Second, cache.read(GoldKarat.K21))
        assertEquals(k24, cache.read(GoldKarat.K24))
    }

    @Test fun sourceChangeStartsANewHistoryInsteadOfJoiningIncomparablePrices() = runTest {
        val cache = GoldRateCache(FaultablePreferences())
        cache.save(rate(GoldKarat.K18, "5319", "2026-09-25T10:00:00Z"))
        val replacement = rate(GoldKarat.K18, "5320", "2026-09-25T10:05:00Z")
            .copy(sourceName = "Replacement provider")
        cache.save(replacement)

        assertEquals(listOf(replacement.toObservation()), cache.readHistory(GoldKarat.K18))
    }

    private fun rate(karat: GoldKarat, buy: String, fetchedAt: String): GoldRate {
        val buyRate = BigDecimal(buy)
        return GoldRate(
            karat = karat,
            buyRate = buyRate,
            sellRate = buyRate.add(BigDecimal("20")),
            sourceName = "Ta3weem",
            sourceUrl = "https://ta3weem.com/en/gold-prices/${karat.ta3weemSlug}",
            sourceDisplayedAt = LocalDateTime.of(2026, 9, 25, 12, 0),
            fetchedAt = Instant.parse(fetchedAt),
        )
    }
}
