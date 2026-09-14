package com.example.currencyraise.data.repository

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.data.local.SettingsStore
import com.example.currencyraise.domain.model.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DefaultSettingsRepositoryTest {
    @Test fun failedWriteLeavesExistingSettingsIntact() = runTest {
        val store = FaultablePreferences()
        val repository = DefaultSettingsRepository(SettingsStore(store, FaultablePreferences()))
        repository.setUpdateInterval(UpdateInterval.TWO_HOURS)
        store.writeFailure = IOException("disk unavailable")
        assertEquals(SettingsWriteResult.STORAGE_FAILURE, repository.setNotificationsEnabled(false))
        assertEquals(AppSettings(updateInterval = UpdateInterval.TWO_HOURS), repository.observeSettings().first())
    }

    @Test fun readFailureIsNotMistakenForDefaults() = runTest {
        val store = FaultablePreferences()
        store.readFailure = IOException("unreadable")
        val repository = DefaultSettingsRepository(SettingsStore(store, FaultablePreferences()))
        try {
            repository.observeSettings().first()
            fail("Expected explicit storage failure")
        } catch (e: StorageReadException) {
            assertTrue(e.cause is IOException)
        }
    }

    @Test fun invalidIntervalIsReportedAndExplicitSelectionRepairsIt() = runTest {
        val store = FaultablePreferences()
        store.values.value = mutablePreferencesOf(intPreferencesKey("interval_hours") to 3)
        val repository = DefaultSettingsRepository(SettingsStore(store, FaultablePreferences()))
        try {
            repository.observeSettings().first()
            fail("Unknown intervals cannot silently become hourly")
        } catch (_: StorageReadException) {
            assertEquals(SettingsWriteResult.SAVED, repository.setUpdateInterval(UpdateInterval.FOUR_HOURS))
            assertEquals(UpdateInterval.FOUR_HOURS, repository.observeSettings().first().updateInterval)
        }
    }

    @Test fun cancellationIsNotReportedAsStorageFailure() = runTest {
        val store = FaultablePreferences()
        store.writeFailure = CancellationException("cancel write")
        try {
            DefaultSettingsRepository(SettingsStore(store, FaultablePreferences())).setNotificationsEnabled(false)
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertTrue(SettingsStore(store, FaultablePreferences()).observe().first().notificationsEnabled)
        }
    }
}
