package com.example.currencyraise.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideCallFactory(): Call.Factory = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        // No body/header logging; no cookies or credentials are required by this provider.
        .build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
