package com.example.currencyraise.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun quote(
    buy: String = "51.2700",
    sell: String = "51.3700",
    fetchedAt: String = "2026-09-11T10:00:00Z",
) = ExchangeRate(
    baseCurrency = "USD", quoteCurrency = "EGP",
    buyRate = BigDecimal(buy), sellRate = BigDecimal(sell),
    sourceId = "banque_misr", sourceName = "Banque Misr",
    sourceUrl = "https://www.banquemisr.com/",
    quoteKind = QuoteKind.CASH,
    sourceDisplayedAt = LocalDateTime.of(2026, 9, 10, 14, 28, 9),
    sourceQuoteId = "2026091015", fetchedAt = Instant.parse(fetchedAt),
)

/** Fault injection at the DataStore boundary; real file persistence is tested separately. */
internal class FaultablePreferences : DataStore<Preferences> {
    val values = MutableStateFlow<Preferences>(emptyPreferences())
    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null
    private val lock = Mutex()
    override val data = flow {
        readFailure?.let { throw it }
        emitAll(values)
    }
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        lock.withLock {
            writeFailure?.let { throw it }
            transform(values.value).also { values.value = it }
        }
}
