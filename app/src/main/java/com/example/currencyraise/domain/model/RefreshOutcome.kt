package com.example.currencyraise.domain.model

sealed interface RefreshOutcome {
    data class Success(val rate: ExchangeRate, val change: RateChange) : RefreshOutcome
    data class Failure(val error: RefreshError) : RefreshOutcome
    data object AlreadyRefreshing : RefreshOutcome
}

enum class RateChange { FIRST_QUOTE, UNCHANGED, CHANGED }

sealed interface RefreshError {
    data class Fetch(val error: RateFetchError) : RefreshError
    data object StorageRead : RefreshError
    data object StorageWrite : RefreshError
}

/** Observe failures are explicit; callers retain their last UI value and offer a new subscription. */
class StorageReadException(cause: Throwable) : Exception("Saved data could not be read", cause)
