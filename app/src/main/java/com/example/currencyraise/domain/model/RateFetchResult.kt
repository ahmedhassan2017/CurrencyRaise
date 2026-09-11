package com.example.currencyraise.domain.model

sealed interface RateFetchResult {
    data class Success(val rate: ExchangeRate) : RateFetchResult
    data class Failure(val error: RateFetchError) : RateFetchResult
}

/** Technical details are classified here; presentation supplies localized messages later. */
sealed interface RateFetchError {
    data object Network : RateFetchError
    data object Timeout : RateFetchError
    data object InvalidResponse : RateFetchError
    data class Http(val statusCode: Int, val retryAfter: String? = null) : RateFetchError
}
