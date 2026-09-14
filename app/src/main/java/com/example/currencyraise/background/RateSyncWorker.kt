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
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = when (runner.run(runAttemptCount)) {
        BackgroundResult.COMPLETED -> Result.success()
        BackgroundResult.RETRY -> Result.retry()
        // Periodic work will run again next interval even after failure.
        BackgroundResult.FAILED -> Result.failure()
    }
}
