package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.AppearanceMode
import com.example.currencyraise.domain.model.UpdateInterval
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine

internal class SettingsStore(
    private val store: DataStore<Preferences>,
    private val deviceState: DataStore<Preferences>,
) {
    fun observe(): Flow<AppSettings> = combine(store.data, deviceState.data) { prefs, device ->
        try {
            val hours = prefs[INTERVAL] ?: UpdateInterval.ONE_HOUR.hours
            val interval = UpdateInterval.entries.singleOrNull { it.hours == hours }
                ?: throw IOException("Unrecognized saved update interval")
            val appearanceName = prefs[APPEARANCE] ?: AppearanceMode.SYSTEM.name
            val appearance = AppearanceMode.entries.singleOrNull { it.name == appearanceName }
                ?: throw IOException("Unrecognized saved appearance mode")
            AppSettings(
                updateInterval = interval,
                automaticChecksEnabled = prefs[AUTOMATIC] ?: true,
                notificationsEnabled = prefs[NOTIFICATIONS] ?: true,
                notificationPermissionAsked = device[PERMISSION_ASKED] ?: false,
                appearanceMode = appearance,
            )
        } catch (e: ClassCastException) {
            throw IOException("Invalid saved setting type", e)
        }
    }.distinctUntilChanged()

    suspend fun setInterval(interval: UpdateInterval) {
        store.edit { it[INTERVAL] = interval.hours }
    }

    suspend fun setAutomatic(enabled: Boolean) {
        store.edit { it[AUTOMATIC] = enabled }
    }

    suspend fun setNotifications(enabled: Boolean) {
        store.edit { it[NOTIFICATIONS] = enabled }
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        store.edit { it[APPEARANCE] = mode.name }
    }

    suspend fun markPermissionAsked() {
        deviceState.edit { it[PERMISSION_ASKED] = true }
    }

    private companion object {
        val INTERVAL = intPreferencesKey("interval_hours")
        val AUTOMATIC = booleanPreferencesKey("automatic_checks")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val APPEARANCE = stringPreferencesKey("appearance_mode")
    }
}
