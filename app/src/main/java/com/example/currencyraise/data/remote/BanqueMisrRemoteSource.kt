package com.example.currencyraise.data.remote

import com.example.currencyraise.data.mapper.BANQUE_MISR_URL
import com.example.currencyraise.data.mapper.toExchangeRate
import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.model.RateFetchResult
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

internal class BanqueMisrRemoteSource @Inject constructor(
    private val calls: Call.Factory,
    private val parser: BanqueMisrParser,
    private val clock: Clock,
) : RateRemoteSource {
    /** OkHttp owns the IO thread; cancellation cancels the actual HTTP call. */
    override suspend fun fetchLatest(): RateFetchResult = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(BANQUE_MISR_URL)
            .header("Accept", "text/html")
            .header("User-Agent", "CurrencyRaise/0.1 (personal exchange-rate reader)")
            .build()
        val call = calls.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    if (!continuation.isActive) return
                    try {
                        readQuote(it)
                    } catch (_: InvalidRatePageException) {
                        RateFetchResult.Failure(RateFetchError.InvalidResponse)
                    } catch (e: IOException) {
                        failure(e)
                    }
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private fun readQuote(response: Response): RateFetchResult {
        if (!response.isSuccessful) {
            return RateFetchResult.Failure(
                RateFetchError.Http(response.code, response.header("Retry-After")?.take(128))
            )
        }
        val body = response.body
        val mediaType = body.contentType()
        if (mediaType?.type != "text" || mediaType.subtype != "html") {
            return RateFetchResult.Failure(RateFetchError.InvalidResponse)
        }
        // Enforce the cap on decompressed content, including chunked responses with no length.
        val source = body.source()
        source.request(MAX_PAGE_BYTES + 1)
        if (source.buffer.size > MAX_PAGE_BYTES) {
            return RateFetchResult.Failure(RateFetchError.InvalidResponse)
        }
        return RateFetchResult.Success(parser.parse(body.string()).toExchangeRate(clock.instant()))
    }

    private fun failure(error: IOException) = RateFetchResult.Failure(
        if (error is InterruptedIOException) RateFetchError.Timeout else RateFetchError.Network
    )

    private companion object {
        const val MAX_PAGE_BYTES = 512L * 1024
    }
}
