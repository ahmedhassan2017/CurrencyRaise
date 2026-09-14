package com.example.currencyraise

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.currencyraise.background.WorkSyncScheduler
import com.example.currencyraise.notification.RateNotificationPublisher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CurrencyRaiseApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject internal lateinit var scheduler: WorkSyncScheduler
    @Inject internal lateinit var notifications: RateNotificationPublisher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        scheduler.start()
    }
}
