package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.GoldKarat

interface GoldAlertStateRepository {
    /** Atomically saves [event] and returns the event previously handled for [karat]. */
    suspend fun recordHandledQuote(karat: GoldKarat, event: String): String?
}
