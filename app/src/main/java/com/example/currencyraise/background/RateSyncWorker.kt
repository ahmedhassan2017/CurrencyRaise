package com.example.currencyraise.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
internal class RateSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val runner: BankBackgroundRefreshRunner,
    private val rescheduler: RateSyncRescheduler,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return when (runner.run(runAttemptCount)) {
            BackgroundResult.RETRY -> Result.retry()
            BackgroundResult.COMPLETED, BackgroundResult.FAILED -> try {
                // A one-time request is used because Android periodic work has a 15-minute minimum.
                rescheduler.scheduleNext()
                Result.success()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                // Retrying is safe: repositories suppress a second provider call inside five minutes.
                Result.retry()
            }
        }
    }
}
