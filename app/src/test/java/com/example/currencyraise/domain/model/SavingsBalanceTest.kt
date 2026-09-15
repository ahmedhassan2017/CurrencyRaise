package com.example.currencyraise.domain.model

import com.example.currencyraise.data.quote
import java.math.BigDecimal
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
        assertNull(SavingsBalance.EMPTY.valueAt(quote().copy(baseCurrency = "EUR")))
    }
}
