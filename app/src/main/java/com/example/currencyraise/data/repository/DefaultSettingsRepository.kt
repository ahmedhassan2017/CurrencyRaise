package com.example.currencyraise.data.repository

import com.example.currencyraise.data.local.SettingsStore
import com.example.currencyraise.domain.model.SettingsWriteResult
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.model.UpdateInterval
import com.example.currencyraise.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch

internal class DefaultSettingsRepository(
    private val settings: SettingsStore,
) : SettingsRepository {
    override fun observeSettings() = settings.observe().catch { error ->
        if (error is IOException) throw StorageReadException(error)
        throw error
    }

    override suspend fun setUpdateInterval(interval: UpdateInterval) =
        write { settings.setInterval(interval) }

    override suspend fun setAutomaticChecksEnabled(enabled: Boolean) =
        write { settings.setAutomatic(enabled) }

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        write { settings.setNotifications(enabled) }

    private suspend fun write(block: suspend () -> Unit): SettingsWriteResult = try {
        block()
        SettingsWriteResult.SAVED
    } catch (_: IOException) {
        SettingsWriteResult.STORAGE_FAILURE
    }
}
