package com.example.currencyraise.notification

import com.example.currencyraise.data.quote
import com.example.currencyraise.domain.model.QuoteKind
import com.example.currencyraise.domain.model.RateDirection
import org.junit.Assert.*
import org.junit.Test

class RateAlertTest {
    @Test fun missingOrDifferentBankPairOrKindCannotInventDirections() {
        val current = quote()
        assertNull(RateAlert(current).buyMovement)
        for (previous in listOf(
            quote().copy(sourceId = "cib_ta3weem"), quote().copy(baseCurrency = "EUR"),
            quote().copy(quoteCurrency = "GBP"), quote().copy(quoteKind = QuoteKind.BANK_RATE),
        )) {
            assertNull(RateAlert(current, previous).buyMovement)
            assertNull(RateAlert(current, previous).sellMovement)
        }
    }

    @Test fun mixedAndUnchangedDirectionsUseIndependentDecimals() {
        val previous = quote(buy = "51.20", sell = "51.40")
        val mixed = RateAlert(quote(buy = "51.21", sell = "51.39"), previous)
        assertEquals(RateDirection.UP, mixed.buyMovement!!.direction)
        assertEquals(RateDirection.DOWN, mixed.sellMovement!!.direction)
        assertEquals("0.01", mixed.buyMovement.difference.toPlainString())
        assertEquals("-0.01", mixed.sellMovement.difference.toPlainString())
        val oneSide = RateAlert(quote(buy = "51.2000", sell = "51.39"), previous)
        assertEquals(RateDirection.UNCHANGED, oneSide.buyMovement!!.direction)
        assertEquals(RateDirection.DOWN, oneSide.sellMovement!!.direction)
    }
}
