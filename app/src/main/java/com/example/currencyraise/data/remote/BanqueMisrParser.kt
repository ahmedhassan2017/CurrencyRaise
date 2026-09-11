package com.example.currencyraise.data.remote

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import javax.inject.Inject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal data class BanqueMisrQuote(
    val buy: BigDecimal,
    val sell: BigDecimal,
    val displayedAt: LocalDateTime?,
    val quoteId: String?,
)

internal class InvalidRatePageException : Exception("Unrecognized or invalid Banque Misr rate page")

internal class BanqueMisrParser @Inject constructor() {
    fun parse(html: String): BanqueMisrQuote {
        val document = Jsoup.parse(html)
        // Fail closed on rejection/login/error pages, including those returned as HTTP 200.
        val section = document.select("section#generic-table.exchange-rates").singleOrNull()
            ?: invalid()
        if (section.select("h1").singleOrNull()?.text() != "Exchange rate and currencies") invalid()
        if (section.select(".generic-details-body").singleOrNull()?.text() !=
            "All Rates Are In Egyptian Pounds"
        ) invalid()

        val table = section.select("table").singleOrNull() ?: invalid()
        val headers = table.select("thead > tr")
        if (headers.size != 2) invalid()
        val groups = headers[0].cells()
        if (groups.map { it.text() } != listOf("Currency", "Notes", "Transfer")) invalid()
        if (groups[0].attr("rowspan") != "2" ||
            groups[1].attr("colspan") != "2" || groups[2].attr("colspan") != "2"
        ) invalid()
        if (headers[1].cells().map { it.text() } != listOf("Buy", "Sell", "Buy", "Sell")) invalid()

        val row = table.select("tbody > tr").filter {
            it.cells().firstOrNull()?.text() == "US Dollar"
        }.singleOrNull() ?: invalid()
        val cells = row.cells()
        if (cells.size != 5 || cells.any { it.hasAttr("colspan") || it.hasAttr("rowspan") }) invalid()
        val buy = decimal(cells[1].text())
        val sell = decimal(cells[2].text())
        if (sell < buy) invalid()

        val dateElements = section.select(".generic-details-title > p")
        if (dateElements.size > 1) invalid()
        val displayedAt = dateElements.singleOrNull()?.text()?.let { text ->
            try {
                LocalDateTime.parse(text, TIMESTAMP_FORMAT)
            } catch (_: DateTimeParseException) {
                invalid()
            }
        }
        val quoteElements = section.select("h4").filter { it.text().startsWith("Quotes:") }
        if (quoteElements.size > 1) invalid()
        val quoteId = quoteElements.singleOrNull()?.text()?.let {
            QUOTE_ID.matchEntire(it)?.groupValues?.get(1) ?: invalid()
        }
        return BanqueMisrQuote(buy, sell, displayedAt, quoteId)
    }

    private fun Element.cells(): List<Element> =
        children().filter { it.normalName() == "td" || it.normalName() == "th" }

    private fun decimal(text: String): BigDecimal {
        // No locale guessing, thousands separators, scientific notation or unbounded numbers.
        if (!DECIMAL.matches(text)) invalid()
        return text.toBigDecimal().also { if (it.signum() <= 0) invalid() }
    }

    private fun invalid(): Nothing = throw InvalidRatePageException()

    private companion object {
        val DECIMAL = Regex("[0-9]{1,9}(\\.[0-9]{1,9})?")
        val QUOTE_ID = Regex("Quotes: ([0-9]{1,32})")
        val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm:ss", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT)
    }
}
