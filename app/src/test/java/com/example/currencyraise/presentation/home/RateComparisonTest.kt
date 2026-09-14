package com.example.currencyraise.presentation.home

import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.quote
import com.example.currencyraise.domain.model.RateDirection
import com.example.currencyraise.domain.model.RateMovement
import com.example.currencyraise.domain.model.toObservation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RateComparisonTest {
    private val previous = quote()
    private val current = previous.copy(
        buyRate = previous.buyRate + "0.02".toBigDecimal(),
        sellRate = previous.sellRate - "0.01".toBigDecimal(),
        fetchedAt = previous.fetchedAt.plusSeconds(3600),
    )
    private fun state() = HomeUiState(
        rate = current, history = listOf(previous.toObservation(), current.toObservation()),
        loadingHistory = false,
    )

    @Test fun mixedDirectionsUseExactIndependentDecimals() {
        val comparison = state().rateComparison!!
        assertEquals(RateDirection.UP, comparison.buy.direction)
        assertEquals(RateDirection.DOWN, comparison.sell.direction)
        assertEquals(0, comparison.buy.difference.compareTo("0.02".toBigDecimal()))
        assertEquals(0, comparison.sell.difference.compareTo("-0.01".toBigDecimal()))
        assertEquals(previous.fetchedAt, comparison.previousCheck)
    }

    @Test fun decimalScaleIsNotMovementAndTinyChangesRemainExact() {
        assertEquals(RateDirection.UNCHANGED, RateMovement.between("51.27".toBigDecimal(), "51.2700".toBigDecimal()).direction)
        val tiny = RateMovement.between("51.270000001".toBigDecimal(), "51.270000002".toBigDecimal())
        assertEquals("0.000000001", tiny.difference.toPlainString())
        assertEquals(RateDirection.UP, tiny.direction)
        assertEquals(RateDirection.DOWN, RateMovement.between("51.37".toBigDecimal(), "51.27".toBigDecimal()).direction)
    }

    @Test fun unchangedFollowupClearsPreviousMovement() {
        val next = current.copy(fetchedAt = current.fetchedAt.plusSeconds(3600))
        val comparison = compareLatestRate(next, state().history + next.toObservation())!!
        assertEquals(RateDirection.UNCHANGED, comparison.buy.direction)
        assertEquals(RateDirection.UNCHANGED, comparison.sell.direction)
    }

    @Test fun firstQuoteAndMissingHistoryHaveNoDirection() {
        assertNull(compareLatestRate(previous, listOf(previous.toObservation())))
        assertNull(compareLatestRate(current, emptyList()))
        assertNull(compareLatestRate(null, state().history))
    }

    @Test fun loadingReadFailuresAndOutOfSyncEmissionsCannotShowOldArrows() {
        assertNull(state().copy(loadingHistory = true).rateComparison)
        assertNull(state().copy(historyReadFailed = true).rateComparison)
        assertNull(state().copy(rateReadFailed = true).rateComparison)
        assertNull(state().copy(rate = previous).rateComparison)
        assertNull(state().copy(rate = current.copy(buyRate = previous.buyRate)).rateComparison)
        assertNull(state().copy(rate = current.copy(sellRate = previous.sellRate)).rateComparison)
        assertNull(compareLatestRate(current, listOf(current.toObservation(), previous.toObservation())))
    }

    @Test fun failedRefreshKeepsComparisonForSavedQuote() {
        assertEquals(state().rateComparison, state().copy(refreshError = HomeError.NETWORK).rateComparison)
    }

    @Test fun reopeningCacheRestoresComparisonAndBankChangeResetsIt() = runTest {
        val store = FaultablePreferences()
        val cache = RateCache(store)
        cache.save(previous)
        cache.save(current)
        val reopened = RateCache(store)
        assertEquals(state().rateComparison, compareLatestRate(reopened.read(), reopened.readHistory()))
        val otherSource = current.copy(sourceId = "other_bank", fetchedAt = current.fetchedAt.plusSeconds(3600))
        reopened.save(otherSource)
        assertNull(compareLatestRate(reopened.read(), reopened.readHistory()))
    }
}
