package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.RateFetchResult

/** Boundary for the selected external provider, including deterministic test substitutes. */
internal fun interface RateRemoteSource {
    suspend fun fetchLatest(): RateFetchResult
}
