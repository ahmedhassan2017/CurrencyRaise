package com.example.currencyraise.background

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Both providers finish independently before deciding whether the worker needs a retry. */
internal class BankBackgroundRefreshRunner(private val runners: List<BackgroundRefreshRunner>) {
    suspend fun run(attempt: Int): BackgroundResult = coroutineScope {
        val results = runners.map { runner -> async { runner.run(attempt) } }.awaitAll()
        when {
            BackgroundResult.RETRY in results -> BackgroundResult.RETRY
            BackgroundResult.FAILED in results -> BackgroundResult.FAILED
            else -> BackgroundResult.COMPLETED
        }
    }
}
