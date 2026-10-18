package com.example.currencyraise.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.AppearanceMode
import com.example.currencyraise.domain.model.SettingsWriteResult
import com.example.currencyraise.domain.model.UpdateInterval
import com.example.currencyraise.domain.repository.SettingsRepository
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Diagnostic reproductions of current behavior, not assertions that this behavior is desirable. */
class BackgroundReliabilityInvestigationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun unexpectedProviderExceptionEscapesWithoutSchedulingAnotherCheck() = runBlocking {
        var reschedules = 0
        var healthyProviderCompleted = false
        val runners = BankBackgroundRefreshRunner(listOf(
            BackgroundRateRunner {
                delay(1_000)
                healthyProviderCompleted = true
                BackgroundResult.COMPLETED
            },
            BackgroundRateRunner { throw IllegalStateException("Injected provider failure") },
        ))
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = RateSyncWorker(
                appContext, workerParameters, runners, RateSyncRescheduler { reschedules++ },
            )
        }
        val worker = TestListenableWorkerBuilder<RateSyncWorker>(context)
            .setWorkerFactory(factory).build()
        try {
            worker.doWork()
            fail("Expected the injected exception to escape the current worker")
        } catch (error: IllegalStateException) {
            assertEquals("Injected provider failure", error.message)
        }
        assertFalse(healthyProviderCompleted)
        assertEquals(0, reschedules)
        println("REPRO: provider exception cancels the other provider; next checks scheduled = $reschedules")
    }

    @Test fun failedChainStaysFinishedUntilConfigurationIsAppliedAgain() = runBlocking {
        val name = "diagnostic_failed_sync_${UUID.randomUUID()}"
        val manager = WorkManager.getInstance(context)
        val scheduler = WorkSyncScheduler(context, InvestigationSettings())
        val request = OneTimeWorkRequestBuilder<InjectedProviderFailureWorker>().build()
        try {
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request).await()
            val failed = withTimeout(30_000) {
                manager.getWorkInfosForUniqueWorkFlow(name).first { work ->
                    work.isNotEmpty() && work.all { it.state.isFinished }
                }
            }
            assertEquals(WorkInfo.State.FAILED, failed.single().state)
            delay(500)
            assertTrue(manager.getWorkInfosForUniqueWork(name).get().all { it.state.isFinished })
            scheduler.applyConfiguration(AppSettings(), name)
            val recovered = manager.getWorkInfosForUniqueWork(name).get().single { !it.state.isFinished }
            assertNotEquals(request.id, recovered.id)
            println("REPRO: failed one-time chain has no active work; applying startup configuration recreates ${recovered.id}")
        } finally {
            manager.cancelUniqueWork(name).await()
        }
    }

    @Test fun applyingSameSettingsReplacesAnExistingTwoNodeChain() = runBlocking {
        val name = "diagnostic_chain_handoff_${UUID.randomUUID()}"
        val manager = WorkManager.getInstance(context)
        val scheduler = WorkSyncScheduler(context, InvestigationSettings())
        try {
            scheduler.applyConfiguration(AppSettings(), name)
            val original = manager.getWorkInfosForUniqueWork(name).get().single { !it.state.isFinished }
            val successor = OneTimeWorkRequestBuilder<RateSyncWorker>()
                .setInitialDelay(1, TimeUnit.HOURS)
                .addTag("rate_sync_interval_60_minutes")
                .build()
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, successor).await()
            assertEquals(2, manager.getWorkInfosForUniqueWork(name).get().count { !it.state.isFinished })
            scheduler.applyConfiguration(AppSettings(), name)
            val replacement = manager.getWorkInfosForUniqueWork(name).get().single { !it.state.isFinished }
            assertNotEquals(original.id, replacement.id)
            assertNotEquals(successor.id, replacement.id)
            println("REPRO: unchanged settings replace both pending chain entries with ${replacement.id}")
        } finally {
            manager.cancelUniqueWork(name).await()
        }
    }
}

/** Runs the production worker with a deterministic provider exception and no provider HTTP traffic. */
class InjectedProviderFailureWorker(context: Context, private val parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = RateSyncWorker(
        applicationContext,
        parameters,
        BankBackgroundRefreshRunner(listOf(
            BackgroundRateRunner { throw IllegalStateException("Injected provider failure") },
        )),
        RateSyncRescheduler { error("The exception should escape before rescheduling") },
    ).doWork()
}

private class InvestigationSettings : SettingsRepository {
    override fun observeSettings() = MutableStateFlow(AppSettings())
    override suspend fun setUpdateInterval(interval: UpdateInterval): SettingsWriteResult = error("Unused")
    override suspend fun setAutomaticChecksEnabled(enabled: Boolean): SettingsWriteResult = error("Unused")
    override suspend fun setNotificationsEnabled(enabled: Boolean): SettingsWriteResult = error("Unused")
    override suspend fun setAppearanceMode(mode: AppearanceMode): SettingsWriteResult = error("Unused")
    override suspend fun markNotificationPermissionAsked(): SettingsWriteResult = error("Unused")
}
