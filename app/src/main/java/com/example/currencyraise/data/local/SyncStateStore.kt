package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.domain.repository.SyncStateRepository
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.first

internal class SyncStateStore(private val store: DataStore<Preferences>) : SyncStateRepository {
    override suspend fun retryNotBefore(): Instant? = try {
        store.data.first()[RETRY_AFTER]?.let(Instant::parse)
    } catch (error: DateTimeParseException) {
        throw IOException("Invalid saved request deadline", error)
    } catch (error: ClassCastException) {
        throw IOException("Invalid saved request deadline type", error)
    }

    override suspend fun deferRequestsUntil(until: Instant) {
        store.edit { it[RETRY_AFTER] = until.toString() }
    }

    override suspend fun recordHandledQuote(event: String): String? {
        var previous: String? = null
        try {
            store.edit {
                previous = it[HANDLED_EVENT]
                it[HANDLED_EVENT] = event
            }
        } catch (error: ClassCastException) {
            throw IOException("Invalid saved notification event", error)
        }
        return previous
    }

    private companion object {
        val RETRY_AFTER = stringPreferencesKey("retry_not_before")
        val HANDLED_EVENT = stringPreferencesKey("handled_quote_event")
    }
}
