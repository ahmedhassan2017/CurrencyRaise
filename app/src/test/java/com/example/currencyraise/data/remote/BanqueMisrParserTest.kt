package com.example.currencyraise.data.remote

import com.example.currencyraise.data.mapper.toExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

class BanqueMisrParserTest {
    private val parser = BanqueMisrParser()
    private fun fixture() = checkNotNull(javaClass.getResource("/banque-misr/usd-cash.html")).readText()
    private fun changedCell(index: Int, value: String): String {
        val doc = Jsoup.parse(fixture())
        doc.select("tbody > tr").first()!!.children()[index].text(value)
        return doc.outerHtml()
    }
    private fun rejects(html: String) {
        assertThrows(InvalidRatePageException::class.java) { parser.parse(html) }
    }

    @Test fun readsCashPairAndPreservesSourceLocalTime() {
        val quote = parser.parse(fixture())
        assertEquals(BigDecimal("51.27"), quote.buy)
        assertEquals(BigDecimal("51.37"), quote.sell)
        assertEquals(LocalDateTime.of(2026, 9, 10, 14, 28, 9), quote.displayedAt)
        assertEquals("2026091015", quote.quoteId)
    }

    @Test fun ignoresDifferentTransferPrices() {
        val quote = parser.parse(changedCell(3, "99.01"))
        assertEquals(BigDecimal("51.27"), quote.buy)
        assertEquals(BigDecimal("51.37"), quote.sell)
    }

    @Test fun preservesDecimalPrecision() {
        assertEquals(BigDecimal("51.270012300"), parser.parse(changedCell(1, "51.270012300")).buy)
    }

    @Test fun rejectsZeroNegativeNonFiniteAndAmbiguousPrices() {
        listOf("0", "-1", "NaN", "Infinity", "", "51,27", "5.127e1", "1 234", "1".repeat(100))
            .forEach { rejects(changedCell(1, it)) }
    }

    @Test fun rejectsReversedSpread() = rejects(changedCell(1, "52.00"))
    @Test fun acceptsEqualBuyAndSell() {
        assertEquals(BigDecimal("51.37"), parser.parse(changedCell(1, "51.37")).buy)
    }
    @Test fun rejectsMissingUsd() = rejects(fixture().replace("US Dollar", "Canadian Dollar"))
    @Test fun rejectsWrongQuoteCurrency() =
        rejects(fixture().replace("All Rates Are In Egyptian Pounds", "All Rates Are In Euros"))
    @Test fun rejectsReorderedGroups() =
        rejects(fixture().replace(">Notes<", ">Transfer<").replace(">Transfer</td></tr>", ">Notes</td></tr>"))
    @Test fun rejectsReversedBuySellHeaders() =
        rejects(fixture().replace(">Buy<", ">Temporary<").replace(">Sell<", ">Buy<").replace(">Temporary<", ">Sell<"))

    @Test fun rejectsIncompleteRow() {
        val doc = Jsoup.parse(fixture())
        doc.select("tbody > tr").first()!!.children()[1].remove()
        rejects(doc.outerHtml())
    }
    @Test fun rejectsDuplicateUsdRows() {
        val doc = Jsoup.parse(fixture())
        doc.select("tbody").first()!!.appendChild(doc.select("tbody > tr").first()!!.clone())
        rejects(doc.outerHtml())
    }
    @Test fun rejectsMergedBodyCells() {
        val doc = Jsoup.parse(fixture())
        doc.select("tbody > tr").first()!!.children()[1].attr("colspan", "2")
        rejects(doc.outerHtml())
    }
    @Test fun rejectsInvalidDateInsteadOfNormalizingIt() =
        rejects(fixture().replace("10-09-2026", "31-02-2026"))
    @Test fun missingSourceTimeRemainsUnknown() {
        val doc = Jsoup.parse(fixture())
        doc.select(".generic-details-title").remove()
        assertNull(parser.parse(doc.outerHtml()).displayedAt)
    }
    @Test fun rejectsRejectionLoginAndEmptyPages() {
        listOf("", "<html><h1>Request Rejected</h1></html>", "<form><input type='password'></form>",
            "<h1>Exchange rate and currencies</h1><p>US Dollar 51.27 51.37</p>")
            .forEach(::rejects)
    }
    @Test fun mapperKeepsIdentityAndFetchTimeSeparate() {
        val fetched = Instant.parse("2026-09-10T20:00:00Z")
        val rate = parser.parse(fixture()).toExchangeRate(fetched)
        assertEquals("USD", rate.baseCurrency)
        assertEquals("EGP", rate.quoteCurrency)
        assertEquals("banque_misr", rate.sourceId)
        assertEquals(QuoteKind.CASH, rate.quoteKind)
        assertEquals(fetched, rate.fetchedAt)
        assertEquals(LocalDateTime.of(2026, 9, 10, 14, 28, 9), rate.sourceDisplayedAt)
    }

    /** Explicit opt-in probe; normal tests never fetch a live banking site. */
    @Test fun parsesFreshCapturedPageWhenProvided() {
        val path = System.getProperty("rateProbeFile")
        assumeNotNull(path)
        val rate = parser.parse(File(checkNotNull(path)).readText()).toExchangeRate(Instant.now())
        assertTrue(rate.buyRate.signum() > 0)
        assertTrue(rate.sellRate >= rate.buyRate)
        println("Captured page parsed: USD/EGP cash buy=${rate.buyRate}, sell=${rate.sellRate}, source-local=${rate.sourceDisplayedAt}")
    }
}
