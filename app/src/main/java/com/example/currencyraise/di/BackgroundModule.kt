package com.example.currencyraise.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.currencyraise.data.local.SyncStateStore
import com.example.currencyraise.domain.repository.SyncStateRepository
import com.example.currencyraise.notification.NotificationSink
import com.example.currencyraise.notification.RateNotificationPublisher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object BackgroundModule {
    @Provides @Singleton
    fun syncState(@ApplicationContext context: Context): SyncStateRepository =
        SyncStateStore(PreferenceDataStoreFactory.create {
            File(context.noBackupFilesDir, "background_state.preferences_pb")
        })

    @Provides
    fun notificationSink(publisher: RateNotificationPublisher): NotificationSink = publisher
}
