package com.example.currencyraise.di

import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.local.SettingsStore
import com.example.currencyraise.data.remote.BanqueMisrRemoteSource
import com.example.currencyraise.data.remote.RateRemoteSource
import com.example.currencyraise.data.repository.DefaultExchangeRateRepository
import com.example.currencyraise.data.repository.DefaultSettingsRepository
import com.example.currencyraise.domain.repository.ExchangeRateRepository
import com.example.currencyraise.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProviderModule {
    @Binds
    abstract fun bindRateSource(source: BanqueMisrRemoteSource): RateRemoteSource
}

@Module
@InstallIn(SingletonComponent::class)
internal object RepositoryModule {
    @Provides
    @Singleton
    fun provideRateRepository(
        source: RateRemoteSource, cache: RateCache,
        state: com.example.currencyraise.domain.repository.SyncStateRepository, clock: java.time.Clock,
    ): ExchangeRateRepository =
        DefaultExchangeRateRepository(source, cache, state, clock)

    @Provides
    @Singleton
    fun provideSettingsRepository(settings: SettingsStore): SettingsRepository =
        DefaultSettingsRepository(settings)
}
