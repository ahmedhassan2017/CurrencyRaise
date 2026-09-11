package com.example.currencyraise.presentation.home

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RateFormattingTest {
    @Test fun decimalPrecisionAndLocaleArePreserved() {
        assertEquals("51.2701", formatRate(BigDecimal("51.2701"), Locale.US))
        assertEquals("51,2701", formatRate(BigDecimal("51.2701"), Locale.GERMANY))
        assertEquals("51.20", formatRate(BigDecimal("51.2"), Locale.US))
    }
    @Test fun fetchInstantUsesDisplayZoneButSourceTimeIsNotConverted() {
        val instant = Instant.parse("2026-09-11T10:30:00Z")
        assertTrue(formatFetchTime(instant, Locale.US, ZoneId.of("UTC")).contains("10:30"))
        assertTrue(formatFetchTime(instant, Locale.US, ZoneId.of("+03:00")).contains("1:30"))
        assertTrue(formatSourceTime(LocalDateTime.parse("2026-09-11T10:30:00"), Locale.US).contains("10:30"))
    }
}
