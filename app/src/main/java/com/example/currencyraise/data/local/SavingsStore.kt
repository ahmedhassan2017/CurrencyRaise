package com.example.currencyraise.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.domain.model.GoldKarat
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class SavingsStore(private val store: DataStore<Preferences>) {
    fun observe(): Flow<SavingsBalance> = store.data.map(::decode).distinctUntilChanged()

    suspend fun save(balance: SavingsBalance) {
        store.edit { preferences ->
            preferences[VERSION] = 2
            preferences[USD] = balance.usd.toPlainString()
            preferences[EGP] = balance.egp.toPlainString()
            preferences[GOLD_GRAMS] = balance.goldGrams.toPlainString()
            preferences[GOLD_KARAT] = balance.goldKarat.name
        }
    }

    private fun decode(preferences: Preferences): SavingsBalance {
        if (preferences.asMap().isEmpty()) return SavingsBalance.EMPTY
        try {
            val version = preferences[VERSION]
            require(version == 1 || version == 2) { "Unknown savings schema" }
            val usd = requireNotNull(preferences[USD]) { "Missing USD savings" }
            val egp = requireNotNull(preferences[EGP]) { "Missing EGP savings" }
            require(AMOUNT.matches(usd) && AMOUNT.matches(egp)) { "Invalid saved amount" }
            if (version == 1) return SavingsBalance(usd.toBigDecimal(), egp.toBigDecimal())
            val goldGrams = requireNotNull(preferences[GOLD_GRAMS]) { "Missing gold savings" }
            val goldKarat = requireNotNull(preferences[GOLD_KARAT]) { "Missing gold karat" }
            require(GOLD_AMOUNT.matches(goldGrams)) { "Invalid saved gold amount" }
            return SavingsBalance(
                usd = usd.toBigDecimal(),
                egp = egp.toBigDecimal(),
                goldGrams = goldGrams.toBigDecimal(),
                goldKarat = GoldKarat.valueOf(goldKarat),
            )
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid saved savings", error)
        } catch (error: ClassCastException) {
            throw IOException("Invalid saved savings type", error)
        }
    }

    private companion object {
        val AMOUNT = Regex("[0-9]{1,15}(\\.[0-9]{1,2})?")
        val GOLD_AMOUNT = Regex("[0-9]{1,15}(\\.[0-9]{1,3})?")
        val VERSION = intPreferencesKey("schema_version")
        val USD = stringPreferencesKey("usd_savings")
        val EGP = stringPreferencesKey("egp_savings")
        val GOLD_GRAMS = stringPreferencesKey("gold_savings_grams")
        val GOLD_KARAT = stringPreferencesKey("gold_savings_karat")
    }
}
