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

@Singleton
internal class WorkSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
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
        val request = PeriodicWorkRequestBuilder<RateSyncWorker>(settings.updateInterval.hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(settings.updateInterval.hours.toLong(), TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(name)
            .build()
        manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request).await()
    }

    companion object { const val UNIQUE_WORK_NAME = "exchange_rate_periodic_sync" }
}
