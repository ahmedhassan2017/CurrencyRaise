package com.example.currencyraise.presentation.home

import com.example.currencyraise.data.quote
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyConversionTest {
    private val rate = quote(buy = "50.00", sell = "52.00")

    @Test fun usdToEgpUsesTheBankBuyRate() {
        val result = convertCurrency(BigDecimal("10"), rate, ConversionDirection.USD_TO_EGP)

        assertEquals(BigDecimal("500.00"), result?.amount)
        assertEquals(rate.buyRate, result?.appliedRate)
    }

    @Test fun egpToUsdUsesTheBankSellRate() {
        val result = convertCurrency(BigDecimal("520"), rate, ConversionDirection.EGP_TO_USD)

        assertEquals(0, BigDecimal("10").compareTo(result?.amount))
        assertEquals(rate.sellRate, result?.appliedRate)
    }

    @Test fun rejectsNegativeAmountsAndUnexpectedCurrencyPairs() {
        assertNull(convertCurrency(BigDecimal("-1"), rate, ConversionDirection.USD_TO_EGP))
        assertNull(
            convertCurrency(
                BigDecimal.ONE,
                rate.copy(baseCurrency = "EUR"),
                ConversionDirection.USD_TO_EGP,
            ),
        )
    }

    @Test fun normalizesWesternAndArabicDecimalInput() {
        assertEquals("123.45", normalizeCurrencyInput("١٢٣٫٤٥"))
        assertEquals("12.50", normalizeCurrencyInput("12,50"))
        assertEquals("0.75", normalizeCurrencyInput(".75"))
        assertEquals("123456789012345.67", normalizeCurrencyInput("12345678901234567.678"))
    }

    @Test fun formatsMoneyToTwoDecimalPlaces() {
        assertEquals("1,234.57", formatCurrencyAmount(BigDecimal("1234.567"), Locale.US))
    }
}
