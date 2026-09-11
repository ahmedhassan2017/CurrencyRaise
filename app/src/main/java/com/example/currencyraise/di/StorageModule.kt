package com.example.currencyraise.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.currencyraise.data.local.RateCache
import com.example.currencyraise.data.local.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object StorageModule {
    // Each singleton owns exactly one DataStore for its dedicated file.
    // No corruption handler silently replaces existing data with empty preferences.
    @Provides
    @Singleton
    fun provideRateCache(@ApplicationContext context: Context): RateCache = RateCache(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("latest_rate") }
    )

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
    )
}
