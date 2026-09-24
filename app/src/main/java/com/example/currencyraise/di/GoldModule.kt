package com.example.currencyraise.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.currencyraise.data.local.GoldRateCache
import com.example.currencyraise.data.local.GoldAlertStateStore
import com.example.currencyraise.data.local.SyncStateStore
import com.example.currencyraise.data.remote.GoldRemoteSource
import com.example.currencyraise.data.remote.Ta3weemGoldRemoteSource
import com.example.currencyraise.data.repository.DefaultGoldRateRepository
import com.example.currencyraise.domain.repository.GoldRateRepository
import com.example.currencyraise.domain.repository.GoldAlertStateRepository
import com.example.currencyraise.domain.repository.SyncStateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class Gold

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GoldSourceModule {
    @Binds
    abstract fun bindGoldSource(source: Ta3weemGoldRemoteSource): GoldRemoteSource
}

@Module
@InstallIn(SingletonComponent::class)
internal object GoldModule {
    @Provides
    @Singleton
    fun cache(@ApplicationContext context: Context) = GoldRateCache(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("gold_rates") }
    )

    @Provides
    @Singleton
    @Gold
    fun syncState(@ApplicationContext context: Context): SyncStateRepository = SyncStateStore(
        PreferenceDataStoreFactory.create {
            File(context.noBackupFilesDir, "gold_sync_state.preferences_pb")
        }
    )

    @Provides
    @Singleton
    fun alertState(@ApplicationContext context: Context): GoldAlertStateRepository = GoldAlertStateStore(
        PreferenceDataStoreFactory.create {
            File(context.noBackupFilesDir, "gold_alert_state.preferences_pb")
        }
    )

    @Provides
    @Singleton
    fun repository(
        source: GoldRemoteSource,
        cache: GoldRateCache,
        @Gold syncState: SyncStateRepository,
        clock: Clock,
    ): GoldRateRepository = DefaultGoldRateRepository(source, cache, syncState, clock)
}
