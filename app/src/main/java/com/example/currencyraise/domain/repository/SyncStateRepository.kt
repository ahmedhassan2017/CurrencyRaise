package com.example.currencyraise.domain.repository

import java.time.Instant

/** Transient device state, excluded from Android backup. Storage failures are explicit IOExceptions. */
interface SyncStateRepository {
    suspend fun retryNotBefore(): Instant?
    suspend fun deferRequestsUntil(until: Instant)
    /** Atomically claims an event and returns the previously handled event, if any. */
    suspend fun recordHandledQuote(event: String): String?
}
