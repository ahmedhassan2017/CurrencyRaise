package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.QuoteKind
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

class CibTa3weemParserTest {
    private val parser = CibTa3weemParser()
    private val fetched = Instant.parse("2026-09-13T12:00:00Z")
    private fun fixture() = checkNotNull(javaClass.getResource("/cib-ta3weem/usd-egp.html")).readText()
    private fun rejects(html: String) {
        assertThrows(InvalidRatePageException::class.java) { parser.parse(html, fetched) }
    }

    @Test fun mapsPricesWithoutMistakingPercentagesForRates() {
        val rate = parser.parse(fixture(), fetched)
        assertEquals("51.34", rate.buyRate.toPlainString())
        assertEquals("51.44", rate.sellRate.toPlainString())
        assertEquals("USD", rate.baseCurrency)
        assertEquals("EGP", rate.quoteCurrency)
        assertEquals("cib_ta3weem", rate.sourceId)
        assertEquals("CIB via Ta3weem", rate.sourceName)
        assertEquals(Bank.CIB.sourceUrl, rate.sourceUrl)
        assertEquals(QuoteKind.BANK_RATE, rate.quoteKind)
        assertEquals(LocalDateTime.of(2026, 9, 13, 14, 54), rate.sourceDisplayedAt)
        assertEquals(fetched, rate.fetchedAt)
        assertNull(rate.sourceQuoteId)
    }

    @Test fun rejectsWrongBankPairAndColumnOrder() {
        rejects(fixture().replace("Commercial International Bank (CIB)", "Banque Misr"))
        rejects(fixture().replace("USD-EGP", "USD-EUR"))
        rejects(fixture().replace("<th>Buy Rate</th><th>Sell Rate</th>", "<th>Sell Rate</th><th>Buy Rate</th>"))
    }

    @Test fun rejectsMissingDuplicateAndIncompleteUsdRows() {
        val doc = Jsoup.parse(fixture())
        val row = doc.selectFirst("tbody > tr")!!
        row.after(row.outerHtml())
        rejects(doc.outerHtml())
        rejects(fixture().replace("US Dollar (USD)", "Euro (EUR)"))
        doc.select("tbody > tr").last()!!.remove()
        doc.select("tbody > tr > td").last()!!.remove()
        rejects(doc.outerHtml())
    }

    @Test fun rejectsBadDecimalsAndReversedSpread() {
        listOf("0", "-1", "1e2", "NaN", "51,34", "9999999999", "51.34 EGP", "52.00")
            .forEach { rejects(fixture().replace("51.34", it)) }
    }

    @Test fun rejectsInvalidTimestampAndExtraPriceSpans() {
        rejects(fixture().replace("13/09/2026", "31/02/2026"))
        rejects(fixture().replace("14:54", "25:00"))
        rejects(fixture().replace("<span>51.34</span>", "<span>51.34</span><span>51.35</span>"))
    }

    @Test fun rejectsChallengeAndLoginPages() {
        listOf("", "<h1>Access denied</h1>", "<form><input type='password'></form>").forEach(::rejects)
    }

    @Test fun parsesFreshCapturedPageWhenProvided() {
        val path = System.getProperty("cibRateProbeFile")
        assumeNotNull(path)
        val rate = parser.parse(File(checkNotNull(path)).readText(), fetched)
        assertTrue(rate.buyRate.signum() > 0)
        assertTrue(rate.sellRate >= rate.buyRate)
        println("Captured CIB page parsed: buy=${rate.buyRate}, sell=${rate.sellRate}, source-local=${rate.sourceDisplayedAt}")
    }
}
