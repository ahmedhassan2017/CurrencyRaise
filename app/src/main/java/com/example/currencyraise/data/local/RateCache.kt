package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class RateCache(private val store: DataStore<Preferences>) {
    fun observe(): Flow<ExchangeRate?> = store.data.map(::decode).distinctUntilChanged()
    suspend fun read(): ExchangeRate? = observe().first()

    /** All fields commit together; a failed commit preserves the previous snapshot. */
    suspend fun save(rate: ExchangeRate) {
        store.edit { prefs ->
            prefs[VERSION] = 1
            prefs[BASE] = rate.baseCurrency
            prefs[QUOTE] = rate.quoteCurrency
            prefs[BUY] = rate.buyRate.toPlainString()
            prefs[SELL] = rate.sellRate.toPlainString()
            prefs[SOURCE_ID] = rate.sourceId
            prefs[SOURCE_NAME] = rate.sourceName
            prefs[SOURCE_URL] = rate.sourceUrl
            prefs[KIND] = rate.quoteKind.name
            prefs[FETCHED] = rate.fetchedAt.toString()
            rate.sourceDisplayedAt?.let { prefs[DISPLAYED] = it.toString() } ?: prefs.remove(DISPLAYED)
            rate.sourceQuoteId?.let { prefs[QUOTE_ID] = it } ?: prefs.remove(QUOTE_ID)
        }
    }

    private fun decode(prefs: Preferences): ExchangeRate? {
        if (prefs.asMap().isEmpty()) return null
        try {
            require(prefs[VERSION] == 1) { "Unknown cache schema" }
            return ExchangeRate(
                baseCurrency = required(prefs, BASE),
                quoteCurrency = required(prefs, QUOTE),
                buyRate = required(prefs, BUY).toBigDecimal(),
                sellRate = required(prefs, SELL).toBigDecimal(),
                sourceId = required(prefs, SOURCE_ID),
                sourceName = required(prefs, SOURCE_NAME),
                sourceUrl = required(prefs, SOURCE_URL),
                quoteKind = QuoteKind.valueOf(required(prefs, KIND)),
                sourceDisplayedAt = prefs[DISPLAYED]?.let(LocalDateTime::parse),
                sourceQuoteId = prefs[QUOTE_ID],
                fetchedAt = Instant.parse(required(prefs, FETCHED)),
            )
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid saved quote", e)
        } catch (e: DateTimeParseException) {
            throw IOException("Invalid saved quote timestamp", e)
        } catch (e: ClassCastException) {
            throw IOException("Invalid saved quote field type", e)
        }
    }

    private fun required(prefs: Preferences, key: Preferences.Key<String>): String =
        requireNotNull(prefs[key]) { "Incomplete saved quote" }

    private companion object {
        val VERSION = intPreferencesKey("schema_version")
        val BASE = stringPreferencesKey("base_currency")
        val QUOTE = stringPreferencesKey("quote_currency")
        val BUY = stringPreferencesKey("buy")
        val SELL = stringPreferencesKey("sell")
        val SOURCE_ID = stringPreferencesKey("source_id")
        val SOURCE_NAME = stringPreferencesKey("source_name")
        val SOURCE_URL = stringPreferencesKey("source_url")
        val KIND = stringPreferencesKey("quote_kind")
        val DISPLAYED = stringPreferencesKey("source_displayed_at")
        val QUOTE_ID = stringPreferencesKey("source_quote_id")
        val FETCHED = stringPreferencesKey("fetched_at")
    }
}
