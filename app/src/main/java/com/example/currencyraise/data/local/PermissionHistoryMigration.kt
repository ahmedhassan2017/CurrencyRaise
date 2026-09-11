package com.example.currencyraise.data.local

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.IOException

internal val PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")

/** Copy before removing the legacy key; DataStore retries interrupted migrations safely. */
internal class PermissionHistoryMigration(
    private val deviceState: DataStore<Preferences>,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData.contains(PERMISSION_ASKED)

    override suspend fun migrate(currentData: Preferences): Preferences {
        try {
            val asked = currentData[PERMISSION_ASKED] ?: false
            deviceState.edit {
                // An interrupted migration must never undo a later permission request.
                if (it[PERMISSION_ASKED] != true) it[PERMISSION_ASKED] = asked
            }
            return currentData.toMutablePreferences().apply { remove(PERMISSION_ASKED) }.toPreferences()
        } catch (error: ClassCastException) {
            throw IOException("Invalid legacy notification permission history", error)
        }
    }

    override suspend fun cleanUp() = Unit
}
