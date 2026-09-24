package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
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

internal class Ta3weemGoldParser @Inject constructor() {
    fun parse(html: String, karat: GoldKarat, fetchedAt: Instant): GoldRate {
        val document = Jsoup.parse(html)
        if (document.select("h1").singleOrNull()?.text() != heading(karat)) invalid()
        val table = document.select("table").singleOrNull { candidate ->
            candidate.select("thead > tr").singleOrNull()?.select("th")?.map { it.text() } ==
                listOf("Source", "Buy Rate", "Sell Rate", "Last Updated")
        } ?: invalid()
        val quotes = table.select("tbody > tr").map(::quote)
        val selected = SOURCE_PRIORITY.firstNotNullOfOrNull { preferred ->
            quotes.singleOrNull { it.sourceName == preferred }
        } ?: invalid()
        if (selected.sell < selected.buy) invalid()
        return GoldRate(
            karat = karat,
            buyRate = selected.buy,
            sellRate = selected.sell,
            sourceName = "${selected.sourceName} via Ta3weem",
            sourceUrl = url(karat),
            sourceDisplayedAt = selected.displayedAt,
            fetchedAt = fetchedAt,
        )
    }

    private fun quote(row: Element): ParsedQuote {
        val cells = row.children()
        if (cells.size != 4 || cells.any {
            it.normalName() != "td" || it.hasAttr("colspan") || it.hasAttr("rowspan")
        }) invalid()
        val sourceName = cells[0].select("a").singleOrNull()?.text()?.trim().orEmpty()
        if (sourceName.isEmpty()) invalid()
        val parts = cells[3].select("span").map { it.text() }
        if (parts.size != 2) invalid()
        val displayedAt = try {
            LocalDateTime.parse(parts.joinToString(" "), TIMESTAMP)
        } catch (_: DateTimeParseException) {
            invalid()
        }
        return ParsedQuote(sourceName, price(cells[1]), price(cells[2]), displayedAt)
    }

    private fun price(cell: Element): BigDecimal {
        val spans = cell.select("span")
        if (spans.size != 2 || !PERCENT.matches(spans[1].text())) invalid()
        val normalized = spans[0].text().replace(",", "")
        if (!DECIMAL.matches(normalized)) invalid()
        return normalized.toBigDecimal().also { if (it.signum() <= 0) invalid() }
    }

    private fun heading(karat: GoldKarat) = when (karat) {
        GoldKarat.K24 -> "24 Karat Gold Price in Egypt Today"
        GoldKarat.K21 -> "21 Karat Gold Price in Egypt Today"
        GoldKarat.K18 -> "18 Karat Gold Price in Egypt Today"
    }

    private fun invalid(): Nothing = throw InvalidRatePageException()

    private data class ParsedQuote(
        val sourceName: String,
        val buy: BigDecimal,
        val sell: BigDecimal,
        val displayedAt: LocalDateTime,
    )

    companion object {
        fun url(karat: GoldKarat) = "https://ta3weem.com/en/gold-prices/${karat.ta3weemSlug}"

        private val SOURCE_PRIORITY = listOf(
            "Gold Bullion EG",
            "eDahab",
            "Information and Decision Support Center (Gold)",
            "BTC",
            "Gold Net Trading (GNT)",
            "Goldady",
        )
        private val DECIMAL = Regex("[0-9]{1,9}(\\.[0-9]{1,9})?")
        private val PERCENT = Regex("-?[0-9]{1,9}(\\.[0-9]{1,9})?%")
        private val TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)
    }
}
