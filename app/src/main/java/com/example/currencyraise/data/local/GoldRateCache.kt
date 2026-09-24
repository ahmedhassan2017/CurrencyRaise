package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.domain.model.toObservation
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class GoldRateCache(private val store: DataStore<Preferences>) {
    fun observe(karat: GoldKarat): Flow<GoldRate?> =
        store.data.map { decode(it, karat) }.distinctUntilChanged()

    suspend fun read(karat: GoldKarat): GoldRate? = observe(karat).first()

    fun observeHistory(karat: GoldKarat): Flow<List<RateObservation>> =
        store.data.map { decodeHistory(it, karat) }.distinctUntilChanged()

    suspend fun readHistory(karat: GoldKarat): List<RateObservation> = observeHistory(karat).first()

    suspend fun save(rate: GoldRate) {
        store.edit { prefs ->
            val keys = keys(rate.karat)
            val previous = decode(prefs, rate.karat)
            val history = if (previous != null && previous.sourceName == rate.sourceName) {
                decodeHistory(prefs, rate.karat)
            } else {
                emptyList()
            }
            prefs[keys.history] = RateHistory.encode(
                RateHistory.append(history, rate.toObservation())
            )
            prefs[keys.version] = 1
            prefs[keys.buy] = rate.buyRate.toPlainString()
            prefs[keys.sell] = rate.sellRate.toPlainString()
            prefs[keys.sourceName] = rate.sourceName
            prefs[keys.sourceUrl] = rate.sourceUrl
            prefs[keys.fetched] = rate.fetchedAt.toString()
            rate.sourceDisplayedAt?.let { prefs[keys.displayed] = it.toString() }
                ?: prefs.remove(keys.displayed)
        }
    }

    private fun decodeHistory(prefs: Preferences, karat: GoldKarat): List<RateObservation> {
        val keys = keys(karat)
        val latest = decode(prefs, karat) ?: return emptyList()
        try {
            // Existing installs begin with the real saved quote; earlier prices are never invented.
            return prefs[keys.history]?.let(RateHistory::decode) ?: listOf(latest.toObservation())
        } catch (error: ClassCastException) {
            throw IOException("Invalid saved gold history field type", error)
        }
    }

    private fun decode(prefs: Preferences, karat: GoldKarat): GoldRate? {
        val keys = keys(karat)
        if (prefs[keys.version] == null) return null
        try {
            require(prefs[keys.version] == 1) { "Unknown gold cache schema" }
            return GoldRate(
                karat = karat,
                buyRate = required(prefs, keys.buy).toBigDecimal(),
                sellRate = required(prefs, keys.sell).toBigDecimal(),
                sourceName = required(prefs, keys.sourceName),
                sourceUrl = required(prefs, keys.sourceUrl),
                sourceDisplayedAt = prefs[keys.displayed]?.let(LocalDateTime::parse),
                fetchedAt = Instant.parse(required(prefs, keys.fetched)),
            ).also {
                require(it.buyRate.signum() > 0 && it.sellRate >= it.buyRate)
                require(it.sourceUrl.startsWith("https://ta3weem.com/"))
            }
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid saved gold quote", error)
        } catch (error: DateTimeParseException) {
            throw IOException("Invalid saved gold quote timestamp", error)
        } catch (error: ClassCastException) {
            throw IOException("Invalid saved gold quote field type", error)
        }
    }

    private fun required(prefs: Preferences, key: Preferences.Key<String>): String =
        requireNotNull(prefs[key]) { "Incomplete saved gold quote" }

    private fun keys(karat: GoldKarat): Keys {
        val prefix = karat.name.lowercase()
        return Keys(
            intPreferencesKey("${prefix}_schema_version"),
            stringPreferencesKey("${prefix}_buy"),
            stringPreferencesKey("${prefix}_sell"),
            stringPreferencesKey("${prefix}_source_name"),
            stringPreferencesKey("${prefix}_source_url"),
            stringPreferencesKey("${prefix}_source_displayed_at"),
            stringPreferencesKey("${prefix}_fetched_at"),
            stringPreferencesKey("${prefix}_observed_history_v1"),
        )
    }

    private data class Keys(
        val version: Preferences.Key<Int>,
        val buy: Preferences.Key<String>,
        val sell: Preferences.Key<String>,
        val sourceName: Preferences.Key<String>,
        val sourceUrl: Preferences.Key<String>,
        val displayed: Preferences.Key<String>,
        val fetched: Preferences.Key<String>,
        val history: Preferences.Key<String>,
    )
}
