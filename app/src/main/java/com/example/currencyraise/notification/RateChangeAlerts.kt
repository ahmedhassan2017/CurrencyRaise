package com.example.currencyraise.notification

import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.domain.repository.SyncStateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface NotificationSink {
    fun publish(alert: RateAlert): Boolean
}

@Singleton
internal class RateChangeAlerts @Inject constructor(
    private val state: SyncStateRepository,
    private val sink: NotificationSink,
) {
    private val mutex = Mutex()

    suspend fun handle(rate: ExchangeRate, change: RateChange, enabled: Boolean, previousRate: ExchangeRate? = null): Boolean = mutex.withLock {
        // Fetch time identifies an event, not price equality (which belongs to the rate repository).
        val event = listOf(rate.sourceId, rate.baseCurrency, rate.quoteCurrency, rate.quoteKind.name,
            rate.buyRate.stripTrailingZeros().toPlainString(), rate.sellRate.stripTrailingZeros().toPlainString(),
            rate.fetchedAt.toString()).joinToString("") { "${it.length}:$it" }
        // Claim before posting: a crash may miss an alert, but must not replay it after restart.
        val previous = state.recordHandledQuote(event)
        if (previous == null || previous == event || change != RateChange.CHANGED || !enabled) return false
        currentCoroutineContext().ensureActive()
        sink.publish(RateAlert(rate, previousRate))
    }
}
