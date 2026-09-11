package com.example.currencyraise.data.mapper

import com.example.currencyraise.data.remote.BanqueMisrQuote
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import java.time.Instant

internal const val BANQUE_MISR_URL =
    "https://www.banquemisr.com/en/CAPITAL-MARKETS/Exchange-Rates-and-Currencies?sc_lang=en"

internal fun BanqueMisrQuote.toExchangeRate(fetchedAt: Instant) = ExchangeRate(
    baseCurrency = "USD",
    quoteCurrency = "EGP",
    buyRate = buy,
    sellRate = sell,
    sourceId = "banque_misr",
    sourceName = "Banque Misr",
    sourceUrl = BANQUE_MISR_URL,
    quoteKind = QuoteKind.CASH,
    sourceDisplayedAt = displayedAt,
    sourceQuoteId = quoteId,
    fetchedAt = fetchedAt,
)
