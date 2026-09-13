package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.data.quote
import com.example.currencyraise.data.repository.DefaultSettingsRepository
import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.SettingsWriteResult
import com.example.currencyraise.domain.model.UpdateInterval
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentStoresTest {
    @get:Rule val temporary = TemporaryFolder()
    private val jobs = mutableListOf<Job>()
    private fun open(name: String): Pair<DataStore<Preferences>, Job> {
        val job = SupervisorJob().also(jobs::add)
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(temporary.root, "$name.preferences_pb") },
        ) to job
    }
    @After fun closeStores() = runBlocking { jobs.forEach { it.cancelAndJoin() } }

    @Test fun backgroundDeadlineAndHandledEventSurviveRecreation() = runBlocking {
        val (first, job) = open("background")
        val state = SyncStateStore(first)
        val deadline = java.time.Instant.parse("2026-09-11T12:00:00Z")
        assertNull(state.retryNotBefore())
        assertNull(state.recordHandledQuote("first-event"))
        state.deferRequestsUntil(deadline)
        job.cancelAndJoin()
        val (reopened, _) = open("background")
        val restored = SyncStateStore(reopened)
        assertEquals(deadline, restored.retryNotBefore())
        assertEquals("first-event", restored.recordHandledQuote("next-event"))
        assertEquals("next-event", restored.recordHandledQuote("next-event"))
    }

    @Test fun invalidBackgroundDeadlineIsNotSilentlyReset() = runBlocking {
        val (store, _) = open("background")
        store.edit { it[stringPreferencesKey("retry_not_before")] = "broken" }
        try {
            SyncStateStore(store).retryNotBefore()
            fail("Expected storage failure")
        } catch (_: IOException) {
            assertEquals("broken", store.data.first()[stringPreferencesKey("retry_not_before")])
        }
    }

    @Test fun quoteSurvivesStoreRecreationWithAllMetadataAndDecimalScale() = runBlocking {
        val (first, job) = open("quote")
        assertNull(RateCache(first).read())
        val original = quote()
        RateCache(first).save(original)
        job.cancelAndJoin()
        val (reopened, _) = open("quote")
        assertEquals(original, RateCache(reopened).read())
        assertEquals("51.2700", RateCache(reopened).read()!!.buyRate.toPlainString())
    }

    @Test fun bankQuotesAndBaselinesRemainSeparateAfterRecreation() = runBlocking {
        val misr = quote()
        val cib = quote(buy = "51.31", sell = "51.41").copy(
            sourceId = "cib_ta3weem", sourceName = "CIB via Ta3weem",
            sourceUrl = com.example.currencyraise.domain.model.Bank.CIB.sourceUrl,
            quoteKind = com.example.currencyraise.domain.model.QuoteKind.BANK_RATE,
            sourceQuoteId = null,
        )
        val (misrStore, misrJob) = open("latest_rate")
        val (cibStore, cibJob) = open("cib_latest_rate")
        RateCache(misrStore).save(misr)
        RateCache(cibStore).save(cib)
        misrJob.cancelAndJoin()
        cibJob.cancelAndJoin()
        assertEquals(misr, RateCache(open("latest_rate").first).read())
        assertEquals(cib, RateCache(open("cib_latest_rate").first).read())
        val (misrState, misrStateJob) = open("background_state")
        val (cibState, cibStateJob) = open("cib_background_state")
        SyncStateStore(misrState).recordHandledQuote("misr-event")
        SyncStateStore(cibState).recordHandledQuote("cib-event")
        misrStateJob.cancelAndJoin()
        cibStateJob.cancelAndJoin()
        assertEquals("misr-event", SyncStateStore(open("background_state").first).recordHandledQuote("next"))
        assertEquals("cib-event", SyncStateStore(open("cib_background_state").first).recordHandledQuote("next"))
    }

    @Test fun clearsOptionalFieldsWhenNextQuoteDoesNotSupplyThem() = runBlocking {
        val (store, _) = open("quote")
        val cache = RateCache(store)
        cache.save(quote())
        val next = quote(fetchedAt = "2026-09-11T11:00:00Z")
            .copy(sourceDisplayedAt = null, sourceQuoteId = null)
        cache.save(next)
        assertEquals(next, cache.read())
    }

    @Test fun existingObserverReceivesWholeSavedSnapshot() = runBlocking<Unit> {
        withTimeout(5_000) {
            val (store, _) = open("quote")
            val cache = RateCache(store)
            val received = Channel<com.example.currencyraise.domain.model.ExchangeRate?>(Channel.UNLIMITED)
            val observer = launch { cache.observe().collect { received.send(it) } }
            assertNull(received.receive())
            val saved = quote()
            cache.save(saved)
            assertEquals(saved, received.receive())
            observer.cancelAndJoin()
            received.close()
        }
    }

    @Test fun settingsDefaultsAndIndependentChangesSurviveRecreation() = runBlocking {
        val (store, job) = open("settings")
        val (deviceState, deviceJob) = open("permission")
        val repository = DefaultSettingsRepository(SettingsStore(store, deviceState))
        assertEquals(AppSettings(), repository.observeSettings().first())
        coroutineScope {
            launch { assertEquals(SettingsWriteResult.SAVED, repository.setUpdateInterval(UpdateInterval.SIX_HOURS)) }
            launch { assertEquals(SettingsWriteResult.SAVED, repository.setAutomaticChecksEnabled(false)) }
            launch { assertEquals(SettingsWriteResult.SAVED, repository.setNotificationsEnabled(false)) }
            launch { assertEquals(SettingsWriteResult.SAVED, repository.markNotificationPermissionAsked()) }
        }
        job.cancelAndJoin()
        deviceJob.cancelAndJoin()
        val (restoredDevice, _) = open("permission")
        val (reopened, _) = open("settings")
        assertEquals(
            AppSettings(UpdateInterval.SIX_HOURS, false, false, notificationPermissionAsked = true),
            DefaultSettingsRepository(SettingsStore(reopened, restoredDevice)).observeSettings().first(),
        )
    }

    @Test fun allAllowedIntervalsRoundTrip() = runBlocking {
        val (store, _) = open("settings")
        val settings = SettingsStore(store, open("permission").first)
        for (interval in UpdateInterval.entries) {
            settings.setInterval(interval)
            assertEquals(interval, settings.observe().first().updateInterval)
        }
    }

    @Test fun malformedSavedQuoteIsReportedAndNotErased() = runBlocking {
        val (store, _) = open("quote")
        val cache = RateCache(store)
        cache.save(quote())
        store.edit { it[stringPreferencesKey("buy")] = "broken" }
        try {
            cache.read()
            fail("Expected read failure")
        } catch (_: IOException) {
            assertEquals("broken", store.data.first()[stringPreferencesKey("buy")])
        }
    }

    @Test fun corruptBinaryFileIsNotSilentlyReset() = runBlocking {
        val file = File(temporary.root, "broken.preferences_pb")
        val original = byteArrayOf(-1, -1, -1, -1)
        file.writeBytes(original)
        val (store, _) = open("broken")
        try {
            RateCache(store).read()
            fail("Expected corruption failure")
        } catch (_: IOException) {
            assertArrayEquals(original, file.readBytes())
        }
    }
}
