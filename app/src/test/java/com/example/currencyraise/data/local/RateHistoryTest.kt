package com.example.currencyraise.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.quote
import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.domain.model.toObservation
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RateHistoryTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private fun point(at: Instant = now, buy: String = "51.27") =
        RateObservation(at, buy.toBigDecimal(), "51.37".toBigDecimal())

    @Test fun upgradeSeedsActualSavedQuoteAndRetainsNextUnchangedCheck() = runTest {
        val store = FaultablePreferences()
        val cache = RateCache(store)
        val first = quote(fetchedAt = now.toString())
        cache.save(first)
        // Simulate the version before history existed, without altering the saved quote fields.
        store.edit { it.remove(stringPreferencesKey("observed_history_v1")) }
        assertEquals(listOf(first.toObservation()), cache.readHistory())
        val next = first.copy(fetchedAt = now.plusSeconds(3600))
        cache.save(next)
        assertEquals(listOf(first.toObservation(), next.toObservation()), cache.readHistory())
    }

    @Test fun failedAtomicSavePreservesBothQuoteAndHistory() = runTest {
        val store = FaultablePreferences()
        val cache = RateCache(store)
        cache.save(quote())
        val before = cache.readHistory()
        store.writeFailure = IOException("full disk")
        try { cache.save(quote(buy = "51.30")); fail("Expected failure") } catch (_: IOException) { }
        assertEquals(quote(), cache.read())
        assertEquals(before, cache.readHistory())
    }

    @Test fun anotherBankOrQuoteKindCannotInheritPreviousHistory() = runTest {
        val cache = RateCache(FaultablePreferences())
        cache.save(quote())
        val cib = quote().copy(sourceId = "cib_ta3weem", fetchedAt = now)
        cache.save(cib)
        assertEquals(listOf(cib.toObservation()), cache.readHistory())
        val otherKind = cib.copy(quoteKind = com.example.currencyraise.domain.model.QuoteKind.BANK_RATE,
            fetchedAt = now.plusSeconds(3600))
        cache.save(otherKind)
        assertEquals(listOf(otherKind.toObservation()), cache.readHistory())
    }

    @Test fun historyAndCacheAreIndependentAcrossBanks() = runTest {
        val misr = RateCache(FaultablePreferences())
        val cib = RateCache(FaultablePreferences())
        misr.save(quote())
        cib.save(quote(buy = "51.29"))
        assertEquals("51.2700", misr.readHistory().single().buyRate.toPlainString())
        assertEquals("51.29", cib.readHistory().single().buyRate.toPlainString())
    }

    @Test fun trimsExpiredPointsAndEnforcesBoundWithoutInventingSamples() {
        val boundary = now.minus(RateHistory.RETENTION)
        val history = listOf(point(boundary.minusSeconds(1)), point(boundary))
        assertEquals(listOf(point(boundary), point()), RateHistory.append(history, point()))
        val dense = (1..RateHistory.MAX_OBSERVATIONS).map { point(now.minusSeconds(it.toLong())) }.reversed()
        val retained = RateHistory.append(dense, point())
        assertEquals(RateHistory.MAX_OBSERVATIONS, retained.size)
        assertEquals(now, retained.last().observedAt)
        assertEquals(dense[1], retained.first())
    }

    @Test fun clockCorrectionsAreSortedAndDuplicateTimestampsReplace() {
        val first = point(now.minusSeconds(120))
        val last = point()
        val middle = point(now.minusSeconds(60))
        val ordered = RateHistory.append(listOf(first, last), middle)
        assertEquals(listOf(first, middle, last), ordered)
        val replacement = point(buy = "51.30")
        assertEquals(listOf(first, middle, replacement), RateHistory.append(ordered, replacement))
    }

    @Test fun malformedHistoryIsReportedWithoutErasingLatestQuote() = runTest {
        val store = FaultablePreferences()
        val cache = RateCache(store)
        cache.save(quote())
        store.edit { it[stringPreferencesKey("observed_history_v1")] = "broken" }
        try { cache.readHistory(); fail("Expected failure") } catch (_: IOException) { }
        assertEquals(quote(), cache.read())
        try { cache.save(quote(buy = "51.30")); fail("Expected failure") } catch (_: IOException) { }
        assertEquals(quote(), cache.read())
    }

    @Test fun codecPreservesDecimalsAndRejectsAmbiguousOrInvalidData() {
        val valid = "2026-09-13T12:00:00Z|51.2700|51.3700"
        assertEquals(valid, RateHistory.encode(RateHistory.decode(valid)))
        listOf("broken", "$valid\n$valid", valid.replace("51.2700", "NaN"),
            valid.replace("51.2700", "52.00"), valid.replace("51.2700", "0"),
            valid.replace("51.2700", "1e2"), valid.replace("12:00:00", "25:00:00"))
            .forEach { value -> assertThrows(IOException::class.java) { RateHistory.decode(value) } }
    }
}
