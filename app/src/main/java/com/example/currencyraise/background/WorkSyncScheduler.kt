package com.example.currencyraise.background

import android.content.Context
import androidx.work.*
import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.UpdateInterval
import com.example.currencyraise.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal sealed interface BackgroundStatus {
    data object Starting : BackgroundStatus
    data class Active(val interval: UpdateInterval) : BackgroundStatus
    data object Disabled : BackgroundStatus
    data object Error : BackgroundStatus
}

internal fun interface RateSyncRescheduler {
    suspend fun scheduleNext()
}

@Singleton
internal class WorkSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : RateSyncRescheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStatus = MutableStateFlow<BackgroundStatus>(BackgroundStatus.Starting)
    val status = mutableStatus.asStateFlow()
    private var observation: Job? = null

    @Synchronized
    fun start() {
        if (observation?.isActive == true) return
        observation = scope.launch {
            settings.observeSettings()
                .map { it.copy(notificationsEnabled = false, notificationPermissionAsked = false) }
                .distinctUntilChanged()
                .onEach {
                    mutableStatus.value = BackgroundStatus.Starting
                    applyConfiguration(it)
                    mutableStatus.value = if (it.automaticChecksEnabled) BackgroundStatus.Active(it.updateInterval)
                        else BackgroundStatus.Disabled
                }
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException || cause !is Exception) throw cause
                    mutableStatus.value = BackgroundStatus.Error
                    delay((5_000L * (1L shl attempt.coerceAtMost(6).toInt())).coerceAtMost(300_000L))
                    true
                }
                .collect()
        }
    }

    /** Unique name can be isolated in integration tests without replacing the app's WorkManager. */
    internal suspend fun applyConfiguration(settings: AppSettings, name: String = UNIQUE_WORK_NAME) {
        val manager = WorkManager.getInstance(context)
        if (!settings.automaticChecksEnabled) {
            manager.cancelUniqueWork(name).await()
            return
        }
        val active = manager.getWorkInfosForUniqueWorkFlow(name).first().filterNot { it.state.isFinished }
        val intervalTag = intervalTag(settings.updateInterval)
        if (active.size == 1 && active.single().periodicityInfo == null && intervalTag in active.single().tags) {
            return
        }
        manager.cancelUniqueWork(name).await()
        manager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.REPLACE,
            request(settings.updateInterval, name),
        ).await()
    }

    override suspend fun scheduleNext() {
        val latest = settings.observeSettings().first()
        if (!latest.automaticChecksEnabled) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(latest.updateInterval, UNIQUE_WORK_NAME),
        ).await()
    }

    private fun request(interval: UpdateInterval, name: String) =
        OneTimeWorkRequestBuilder<RateSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(interval.minutes.toLong(), TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(name)
            .addTag(intervalTag(interval))
            .build()

    private fun intervalTag(interval: UpdateInterval) = "rate_sync_interval_${interval.minutes}_minutes"

    companion object { const val UNIQUE_WORK_NAME = "exchange_rate_periodic_sync" }
}
