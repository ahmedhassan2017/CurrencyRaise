package com.example.currencyraise.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.currencyraise.background.BackgroundRefreshRunner
import com.example.currencyraise.background.BankBackgroundRefreshRunner
import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.local.SyncStateStore
import com.example.currencyraise.data.remote.CibTa3weemRemoteSource
import com.example.currencyraise.data.repository.DefaultExchangeRateRepository
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.repository.BankRateRepositories
import com.example.currencyraise.domain.repository.ExchangeRateRepository
import com.example.currencyraise.domain.repository.SettingsRepository
import com.example.currencyraise.domain.repository.SyncStateRepository
import com.example.currencyraise.notification.NotificationSink
import com.example.currencyraise.notification.RateChangeAlerts
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
internal annotation class Cib

@Module
@InstallIn(SingletonComponent::class)
internal object CibModule {
    @Provides @Singleton @Cib
    fun cache(@ApplicationContext context: Context): RateCache = RateCache(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("cib_latest_rate") }
    )

    @Provides @Singleton @Cib
    fun syncState(@ApplicationContext context: Context): SyncStateRepository = SyncStateStore(
        PreferenceDataStoreFactory.create {
            File(context.noBackupFilesDir, "cib_background_state.preferences_pb")
        }
    )

    @Provides @Singleton @Cib
    fun repository(
        source: CibTa3weemRemoteSource, @Cib cache: RateCache,
        @Cib state: SyncStateRepository, clock: Clock,
    ): ExchangeRateRepository = DefaultExchangeRateRepository(source, cache, state, clock)

    @Provides @Singleton
    fun banks(misr: ExchangeRateRepository, @Cib cib: ExchangeRateRepository) =
        BankRateRepositories(mapOf(Bank.BANQUE_MISR to misr, Bank.CIB to cib))

    @Provides @Singleton
    fun background(
        settings: SettingsRepository, banks: BankRateRepositories,
        misrAlerts: RateChangeAlerts, @Cib state: SyncStateRepository, sink: NotificationSink,
    ) = BankBackgroundRefreshRunner(listOf(
        BackgroundRefreshRunner(settings, banks[Bank.BANQUE_MISR], misrAlerts),
        BackgroundRefreshRunner(settings, banks[Bank.CIB], RateChangeAlerts(state, sink)),
    ))
}
