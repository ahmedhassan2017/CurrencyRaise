package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.GoldFetchResult
import com.example.currencyraise.domain.model.GoldKarat

internal fun interface GoldRemoteSource {
    suspend fun fetchLatest(karat: GoldKarat): GoldFetchResult
}
