package com.example.currencyraise.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.currencyraise.CurrencyRaiseApplication
import com.example.currencyraise.domain.model.*
import com.example.currencyraise.domain.repository.*
import com.example.currencyraise.notification.RateChangeAlerts
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackgroundIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun hiltFactoryCreatesProductionWorker() {
        val app = context as CurrencyRaiseApplication
        val worker = TestListenableWorkerBuilder<RateSyncWorker>(context)
            .setWorkerFactory(app.workManagerConfiguration.workerFactory).build()
        assertTrue(worker is RateSyncWorker)
        worker.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
    }

    @Test fun periodicUpdateKeepsOneRequestAndDisableCancelsIt() = runBlocking {
        val name = "test_rate_sync_" + UUID.randomUUID()
        val manager = WorkManager.getInstance(context)
        val scheduler = WorkSyncScheduler(context, MemorySettings())
        try {
            var id: UUID? = null
            for (interval in UpdateInterval.entries) {
                val selected = AppSettings(updateInterval = interval)
                scheduler.applyConfiguration(selected, name)
                scheduler.applyConfiguration(selected, name)
                val work = manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
                    .filter { !it.state.isFinished }
                assertEquals(1, work.size)
                val info = work.single()
                if (id == null) id = info.id else assertEquals(id, info.id)
                assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
                assertEquals(TimeUnit.HOURS.toMillis(interval.hours.toLong()), info.periodicityInfo!!.repeatIntervalMillis)
                assertEquals(TimeUnit.HOURS.toMillis(interval.hours.toLong()), info.initialDelayMillis)
            }
            scheduler.applyConfiguration(AppSettings(automaticChecksEnabled = false), name)
            assertTrue(manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).all { it.state.isFinished })
            scheduler.applyConfiguration(AppSettings(), name)
            val recreated = manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).single { !it.state.isFinished }
            assertNotEquals(id, recreated.id)
        } finally {
            manager.cancelUniqueWork(name).await()
        }
    }

    @Test fun actualCoroutineWorkerMapsSuccessRetryAndFailureWithoutNetwork() = runBlocking {
        val settings = MemorySettings()
        var fetches = 0
        var outcome: RefreshOutcome = RefreshOutcome.Failure(RefreshError.Fetch(RateFetchError.Network))
        val rates = object : ExchangeRateRepository {
            override fun observeLatestUsdEgpRate() = flowOf<ExchangeRate?>(null)
            override suspend fun refreshUsdEgpRate(minimumAge: Duration): RefreshOutcome {
                fetches++
                assertEquals(Duration.ofMinutes(5), minimumAge)
                return outcome
            }
        }
        val state = object : SyncStateRepository {
            var event: String? = null
            override suspend fun retryNotBefore(): Instant? = null
            override suspend fun deferRequestsUntil(until: Instant) = Unit
            override suspend fun recordHandledQuote(event: String): String? = this.event.also { this.event = event }
        }
        var posts = 0
        val runner = BackgroundRefreshRunner(settings, rates, RateChangeAlerts(state) { posts++; true })
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                RateSyncWorker(appContext, workerParameters, runner)
        }
        suspend fun run(attempt: Int = 0) = TestListenableWorkerBuilder<RateSyncWorker>(context)
            .setWorkerFactory(factory).setRunAttemptCount(attempt).build().doWork()
        assertEquals(ListenableWorker.Result.retry(), run())
        assertEquals(ListenableWorker.Result.failure(), run(2))
        outcome = RefreshOutcome.Failure(RefreshError.Fetch(RateFetchError.InvalidResponse))
        assertEquals(ListenableWorker.Result.failure(), run())
        val first = ExchangeRate("USD", "EGP", BigDecimal("51.27"), BigDecimal("51.37"),
            "banque_misr", "Banque Misr", "https://www.banquemisr.com/", QuoteKind.CASH,
            null, null, Instant.parse("2026-09-11T10:00:00Z"))
        outcome = RefreshOutcome.Success(first, RateChange.FIRST_QUOTE)
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(0, posts)
        outcome = RefreshOutcome.Success(first.copy(buyRate = BigDecimal("51.28")), RateChange.CHANGED)
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(1, posts)
        settings.values.value = AppSettings(automaticChecksEnabled = false)
        val before = fetches
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(before, fetches)
    }

    private class MemorySettings : SettingsRepository {
        val values = MutableStateFlow(AppSettings())
        override fun observeSettings() = values
        override suspend fun setUpdateInterval(interval: UpdateInterval): SettingsWriteResult = error("Unused")
        override suspend fun setAutomaticChecksEnabled(enabled: Boolean): SettingsWriteResult = error("Unused")
        override suspend fun setNotificationsEnabled(enabled: Boolean): SettingsWriteResult = error("Unused")
        override suspend fun markNotificationPermissionAsked(): SettingsWriteResult = error("Unused")
    }
}
