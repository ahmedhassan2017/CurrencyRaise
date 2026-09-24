package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.GoldKarat
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Ta3weemGoldParserTest {
    private val parser = Ta3weemGoldParser()
    private val fetchedAt = Instant.parse("2026-09-24T22:35:00Z")
    private fun fixture() = checkNotNull(javaClass.getResource("/ta3weem-gold/21k.html")).readText()

    @Test fun selectsStableEgyptianDealerAndMapsPerGramSpread() {
        val rate = parser.parse(fixture(), GoldKarat.K21, fetchedAt)
        assertEquals(GoldKarat.K21, rate.karat)
        assertEquals("6205.00", rate.buyRate.toPlainString())
        assertEquals("6225.00", rate.sellRate.toPlainString())
        assertEquals("Gold Bullion EG via Ta3weem", rate.sourceName)
        assertEquals("https://ta3weem.com/en/gold-prices/GOLD21K", rate.sourceUrl)
        assertEquals(LocalDateTime.of(2026, 9, 25, 0, 30), rate.sourceDisplayedAt)
        assertEquals(fetchedAt, rate.fetchedAt)
    }

    @Test fun rejectsWrongMarketMalformedNumbersAndReversedSpread() {
        rejects(fixture().replace("21 Karat", "24 Karat"))
        rejects(fixture().replace("6,205.00", "6,205 EGP"))
        rejects(fixture().replace("6,205.00", "6,230.00"))
        rejects(
            fixture()
                .replace("Gold Bullion EG", "Unknown Seller")
                .replace(">BTC<", ">Another Unknown Seller<")
        )
    }

    @Test fun rejectsChallengePagesAndInvalidTimestamps() {
        rejects("<h1>Attention Required!</h1>")
        rejects(fixture().replace("25/09/2026", "31/02/2026"))
        rejects(fixture().replace("00:30", "25:00"))
    }

    private fun rejects(html: String) {
        assertThrows(InvalidRatePageException::class.java) {
            parser.parse(html, GoldKarat.K21, fetchedAt)
        }
    }
}
