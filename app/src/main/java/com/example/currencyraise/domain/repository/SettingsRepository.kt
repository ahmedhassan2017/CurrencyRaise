package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.AppearanceMode
import com.example.currencyraise.domain.model.SettingsWriteResult
import com.example.currencyraise.domain.model.UpdateInterval
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Read failures throw StorageReadException; they are not disguised as default settings. */
    fun observeSettings(): Flow<AppSettings>
    suspend fun setUpdateInterval(interval: UpdateInterval): SettingsWriteResult
    suspend fun setAutomaticChecksEnabled(enabled: Boolean): SettingsWriteResult
    suspend fun setNotificationsEnabled(enabled: Boolean): SettingsWriteResult
    suspend fun setAppearanceMode(mode: AppearanceMode): SettingsWriteResult
    suspend fun markNotificationPermissionAsked(): SettingsWriteResult
}
