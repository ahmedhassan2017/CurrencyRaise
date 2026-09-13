package com.example.currencyraise.acceptance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import com.example.currencyraise.domain.repository.BankRateRepositories
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import com.example.currencyraise.background.BackgroundRefreshRunner
import com.example.currencyraise.background.BackgroundResult
import com.example.currencyraise.data.local.*
import com.example.currencyraise.data.mapper.toExchangeRate
import com.example.currencyraise.data.remote.BanqueMisrParser
import com.example.currencyraise.data.repository.DefaultExchangeRateRepository
import com.example.currencyraise.data.repository.DefaultSettingsRepository
import com.example.currencyraise.domain.model.*
import com.example.currencyraise.notification.RateChangeAlerts
import com.example.currencyraise.presentation.home.HomeError
import com.example.currencyraise.presentation.home.HomeViewModel
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real parser/cache/repository/ViewModel pipeline, with deterministic network outcomes. */
class RatePipelineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val temporary = TemporaryFolder(context.cacheDir)
    private val jobs = mutableListOf<Job>()
    private val viewModels = ViewModelStore()
    private val now = Instant.parse("2026-09-11T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private fun store(name: String) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob().also(jobs::add) + Dispatchers.IO),
    ) { File(temporary.root, "$name.preferences_pb") }

    private fun quote(): ExchangeRate {
        val html = InstrumentationRegistry.getInstrumentation().context.assets.open("banque-misr/usd-cash.html")
            .bufferedReader().use { it.readText() }
        return BanqueMisrParser().parse(html).toExchangeRate(now)
    }

    private suspend fun model(rates: DefaultExchangeRateRepository, settings: DefaultSettingsRepository) =
        withContext(Dispatchers.Main) {
            HomeViewModel(BankRateRepositories(mapOf(Bank.BANQUE_MISR to rates)), settings, clock,
                SavedStateHandle()).also { viewModels.put("home", it) }
        }

    @After fun close() = runBlocking {
        withContext(Dispatchers.Main) { viewModels.clear() }
        jobs.forEach { it.cancelAndJoin() }
    }

    @Test fun coldOnlineLaunchParsesPersistsAndDisplaysQuote() = runBlocking {
        val expected = quote()
        assertEquals("51.27", expected.buyRate.toPlainString())
        assertEquals("51.37", expected.sellRate.toPlainString())
        assertEquals("2026-09-10T14:28:09", expected.sourceDisplayedAt.toString())
        val cache = RateCache(store("rate"))
        val settings = DefaultSettingsRepository(SettingsStore(store("settings"), store("permission")))
        val state = SyncStateStore(store("sync"))
        var requests = 0
        val rates = DefaultExchangeRateRepository({ requests++; RateFetchResult.Success(expected) }, cache, state, clock)
        val home = model(rates, settings)
        withTimeout(10_000) { home.uiState.first { it.rate == expected && !it.refreshing } }
        assertEquals(expected, cache.read())
        assertEquals(1, requests)
    }

    @Test fun coldOfflineLaunchCanRecoverWithManualRefresh() = runBlocking {
        val expected = quote()
        val cache = RateCache(store("rate"))
        val settings = DefaultSettingsRepository(SettingsStore(store("settings"), store("permission")))
        val state = SyncStateStore(store("sync"))
        var outcome: RateFetchResult = RateFetchResult.Failure(RateFetchError.Network)
        val rates = DefaultExchangeRateRepository({ outcome }, cache, state, clock)
        val home = model(rates, settings)
        val failed = withTimeout(10_000) { home.uiState.first { it.refreshError == HomeError.NETWORK && !it.refreshing } }
        assertNull(failed.rate)
        assertNull(cache.read())
        outcome = RateFetchResult.Success(expected)
        withContext(Dispatchers.Main) { home.refresh() }
        withTimeout(10_000) { home.uiState.first { it.rate == expected && !it.refreshing } }
        assertEquals(expected, cache.read())
    }

    @Test fun failedRefreshKeepsCacheThenBackgroundUpdateReachesOpenHome() = runBlocking {
        val expected = quote()
        val old = expected.copy(buyRate = "51.26".toBigDecimal(), fetchedAt = now.minusSeconds(7200))
        val cache = RateCache(store("rate"))
        cache.save(old)
        val settings = DefaultSettingsRepository(SettingsStore(store("settings"), store("permission")))
        val state = SyncStateStore(store("sync"))
        var outcome: RateFetchResult = RateFetchResult.Failure(RateFetchError.Network)
        val rates = DefaultExchangeRateRepository({ outcome }, cache, state, clock)
        val home = model(rates, settings)
        val failed = withTimeout(10_000) { home.uiState.first { it.refreshError == HomeError.NETWORK && !it.refreshing } }
        assertEquals(old, failed.rate)
        assertEquals(old, cache.read())
        var posts = 0
        outcome = RateFetchResult.Success(expected)
        val runner = BackgroundRefreshRunner(settings, rates, RateChangeAlerts(state) { posts++; true })
        assertEquals(BackgroundResult.COMPLETED, runner.run(0))
        val refreshed = withTimeout(10_000) { home.uiState.first { it.rate == expected } }
        assertNull(refreshed.refreshError)
        assertEquals(0, posts) // First background success establishes the baseline.
    }

    @Test fun closingAndReopeningStoresRetainsRateAndPreferences() = runBlocking {
        val expected = quote()
        val cache = RateCache(store("rate"))
        val settings = SettingsStore(store("settings"), store("permission"))
        cache.save(expected)
        settings.setInterval(UpdateInterval.TWELVE_HOURS)
        settings.setAutomatic(false)
        settings.setNotifications(false)
        settings.markPermissionAsked()
        jobs.toList().forEach { it.cancelAndJoin() }
        assertEquals(expected, RateCache(store("rate")).read())
        assertEquals(AppSettings(UpdateInterval.TWELVE_HOURS, false, false, true),
            SettingsStore(store("settings"), store("permission")).observe().first())
    }
}
