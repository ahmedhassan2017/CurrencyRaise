package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.domain.model.SavingsBalance
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class SavingsStore(private val store: DataStore<Preferences>) {
    fun observe(): Flow<SavingsBalance> = store.data.map(::decode).distinctUntilChanged()

    suspend fun save(balance: SavingsBalance) {
        store.edit { preferences ->
            preferences[VERSION] = 1
            preferences[USD] = balance.usd.toPlainString()
            preferences[EGP] = balance.egp.toPlainString()
        }
    }

    private fun decode(preferences: Preferences): SavingsBalance {
        if (preferences.asMap().isEmpty()) return SavingsBalance.EMPTY
        try {
            require(preferences[VERSION] == 1) { "Unknown savings schema" }
            val usd = requireNotNull(preferences[USD]) { "Missing USD savings" }
            val egp = requireNotNull(preferences[EGP]) { "Missing EGP savings" }
            require(AMOUNT.matches(usd) && AMOUNT.matches(egp)) { "Invalid saved amount" }
            return SavingsBalance(usd.toBigDecimal(), egp.toBigDecimal())
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid saved savings", error)
        } catch (error: ClassCastException) {
            throw IOException("Invalid saved savings type", error)
        }
    }

    private companion object {
        val AMOUNT = Regex("[0-9]{1,15}(\\.[0-9]{1,2})?")
        val VERSION = intPreferencesKey("schema_version")
        val USD = stringPreferencesKey("usd_savings")
        val EGP = stringPreferencesKey("egp_savings")
    }
}
