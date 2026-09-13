package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import javax.inject.Inject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class CibTa3weemParser @Inject constructor() {
    fun parse(html: String, fetchedAt: Instant): ExchangeRate {
        val document = Jsoup.parse(html)
        if (document.select("h1").singleOrNull()?.text() != "Commercial International Bank (CIB)") invalid()
        val table = document.select("table").singleOrNull() ?: invalid()
        if (table.select("thead > tr").size != 1 ||
            table.select("thead > tr > th").map { it.text() } !=
            listOf("Currency", "Buy Rate", "Sell Rate", "Last Updated")
        ) invalid()
        val row = table.select("tbody > tr").filter {
            it.children().firstOrNull()?.text() == "US Dollar (USD)"
        }.singleOrNull() ?: invalid()
        val cells = row.children()
        if (cells.size != 4 || cells.any {
            it.normalName() != "td" || it.hasAttr("colspan") || it.hasAttr("rowspan")
        }) invalid()
        // Verify the denomination from the actual USD row, not the page's navigation links.
        if (cells[0].select("a").singleOrNull()?.attr("href") !=
            "https://ta3weem.com/en/currency-exchange-rates/USD-EGP") invalid()
        val buy = price(cells[1])
        val sell = price(cells[2])
        if (sell < buy) invalid()
        val timeParts = cells[3].select("span").map { it.text() }
        if (timeParts.size != 2) invalid()
        val displayedAt = try {
            LocalDateTime.parse(timeParts.joinToString(" "), TIMESTAMP)
        } catch (_: DateTimeParseException) { invalid() }
        return ExchangeRate(
            baseCurrency = "USD", quoteCurrency = "EGP", buyRate = buy, sellRate = sell,
            sourceId = "cib_ta3weem", sourceName = Bank.CIB.sourceName, sourceUrl = Bank.CIB.sourceUrl,
            // The aggregator does not distinguish cash from transfer prices in this table.
            quoteKind = QuoteKind.BANK_RATE, sourceDisplayedAt = displayedAt,
            sourceQuoteId = null, fetchedAt = fetchedAt,
        )
    }

    private fun price(cell: Element): BigDecimal {
        val spans = cell.select("span")
        if (spans.size != 2 || !PERCENT.matches(spans[1].text())) invalid()
        val text = spans[0].text()
        if (!DECIMAL.matches(text)) invalid()
        return text.toBigDecimal().also { if (it.signum() <= 0) invalid() }
    }

    private fun invalid(): Nothing = throw InvalidRatePageException()

    private companion object {
        val DECIMAL = Regex("[0-9]{1,9}(\\.[0-9]{1,9})?")
        val PERCENT = Regex("-?[0-9]{1,9}(\\.[0-9]{1,9})?%")
        val TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)
    }
}
