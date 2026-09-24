package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.repository.GoldAlertStateRepository
import java.io.IOException

internal class GoldAlertStateStore(
    private val store: DataStore<Preferences>,
) : GoldAlertStateRepository {
    override suspend fun recordHandledQuote(karat: GoldKarat, event: String): String? {
        var previous: String? = null
        try {
            store.edit { preferences ->
                val key = handledEventKey(karat)
                previous = preferences[key]
                preferences[key] = event
            }
        } catch (error: ClassCastException) {
            throw IOException("Invalid saved gold notification event", error)
        }
        return previous
    }

    private fun handledEventKey(karat: GoldKarat) =
        stringPreferencesKey("handled_gold_${karat.name.lowercase()}_event")
}
