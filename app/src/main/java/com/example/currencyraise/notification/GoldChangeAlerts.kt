package com.example.currencyraise.notification

import com.example.currencyraise.domain.model.GoldRate
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.domain.repository.GoldAlertStateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface GoldNotificationSink {
    fun publish(alert: GoldRateAlert): Boolean
}

@Singleton
internal class GoldChangeAlerts @Inject constructor(
    private val state: GoldAlertStateRepository,
    private val sink: GoldNotificationSink,
) {
    private val mutex = Mutex()

    suspend fun handle(
        rate: GoldRate,
        change: RateChange,
        enabled: Boolean,
        previousRate: GoldRate? = null,
    ): Boolean = mutex.withLock {
        val event = listOf(
            rate.karat.name,
            rate.sourceName,
            rate.buyRate.stripTrailingZeros().toPlainString(),
            rate.sellRate.stripTrailingZeros().toPlainString(),
            rate.fetchedAt.toString(),
        ).joinToString("") { "${it.length}:$it" }
        // Each karat has its own durable baseline. Claim before posting to prevent replay after a crash.
        val previous = state.recordHandledQuote(rate.karat, event)
        if (previous == null || previous == event || change != RateChange.CHANGED || !enabled) return false
        currentCoroutineContext().ensureActive()
        sink.publish(GoldRateAlert(rate, previousRate))
    }
}
