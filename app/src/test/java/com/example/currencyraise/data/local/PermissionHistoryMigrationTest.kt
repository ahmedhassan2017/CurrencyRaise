package com.example.currencyraise.data.local

import androidx.datastore.preferences.core.*
import com.example.currencyraise.data.FaultablePreferences
import com.example.currencyraise.domain.model.UpdateInterval
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PermissionHistoryMigrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun legacyChoiceIsMovedBeforeNormalSettingsAreEmitted() = runBlocking {
        val device = FaultablePreferences()
        val path = File(temporary.root, "settings.preferences_pb")
        val legacyJob = SupervisorJob()
        val legacy = PreferenceDataStoreFactory.create(scope = CoroutineScope(legacyJob + Dispatchers.IO)) { path }
        legacy.edit {
            it[PERMISSION_ASKED] = true
            it[intPreferencesKey("interval_hours")] = 6
        }
        legacyJob.cancelAndJoin()
        val job = SupervisorJob()
        val migrated = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            migrations = listOf(PermissionHistoryMigration(device)),
        ) { path }
        try {
            val settings = SettingsStore(migrated, device).observe().first()
            assertTrue(settings.notificationPermissionAsked)
            assertEquals(UpdateInterval.SIX_HOURS, settings.updateInterval)
            assertFalse(migrated.data.first().contains(PERMISSION_ASKED))
            // Restoring just the backup-eligible file onto a new device starts with a fresh prompt history.
            assertFalse(SettingsStore(migrated, FaultablePreferences()).observe().first().notificationPermissionAsked)
        } finally { job.cancelAndJoin() }
    }

    @Test fun deviceWriteFailureLeavesLegacyChoiceAvailableForRetry() = runBlocking {
        val device = FaultablePreferences().apply { writeFailure = IOException("disk full") }
        val migration = PermissionHistoryMigration(device)
        val legacy = mutablePreferencesOf(PERMISSION_ASKED to true)
        try {
            migration.migrate(legacy)
            fail("Expected storage failure")
        } catch (_: IOException) {
            assertTrue(legacy[PERMISSION_ASKED] == true)
            assertNull(device.data.first()[PERMISSION_ASKED])
        }
        device.writeFailure = null
        assertFalse(migration.migrate(legacy).contains(PERMISSION_ASKED))
        assertTrue(device.data.first()[PERMISSION_ASKED] == true)
    }

    @Test fun replayNeverUndoesANewerPermissionRequest() = runBlocking {
        val device = FaultablePreferences()
        device.edit { it[PERMISSION_ASKED] = true }
        val migration = PermissionHistoryMigration(device)
        val legacy = mutablePreferencesOf(PERMISSION_ASKED to false)
        migration.migrate(legacy)
        migration.migrate(legacy)
        assertTrue(device.data.first()[PERMISSION_ASKED] == true)
    }

    @Test fun malformedLegacyValueIsNotErased() = runBlocking {
        val legacy = mutablePreferencesOf(stringPreferencesKey(PERMISSION_ASKED.name) to "invalid")
        try {
            PermissionHistoryMigration(FaultablePreferences()).migrate(legacy)
            fail("Expected invalid history")
        } catch (_: IOException) {
            assertEquals("invalid", legacy[stringPreferencesKey(PERMISSION_ASKED.name)])
        }
    }

    @Test fun permissionRequestsOnlyWriteDeviceState() = runBlocking {
        val preferences = FaultablePreferences()
        val device = FaultablePreferences()
        val settings = SettingsStore(preferences, device)
        settings.markPermissionAsked()
        assertFalse(preferences.data.first().contains(PERMISSION_ASKED))
        assertTrue(settings.observe().first().notificationPermissionAsked)
    }

    @Test fun unreadableDeviceHistoryIsExplicit() = runBlocking {
        val device = FaultablePreferences().apply { readFailure = IOException("cannot read") }
        try {
            SettingsStore(FaultablePreferences(), device).observe().first()
            fail("Expected failure")
        } catch (_: IOException) { }
    }
}
