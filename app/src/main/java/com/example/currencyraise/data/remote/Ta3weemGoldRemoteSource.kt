package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.GoldFetchResult
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.RateFetchError
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

internal class Ta3weemGoldRemoteSource @Inject constructor(
    private val calls: Call.Factory,
    private val parser: Ta3weemGoldParser,
    private val clock: Clock,
) : GoldRemoteSource {
    override suspend fun fetchLatest(karat: GoldKarat): GoldFetchResult =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(Ta3weemGoldParser.url(karat))
                .header("Accept", "text/html")
                .header("User-Agent", "CurrencyRaise/0.1 (personal market-rate reader)")
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
                            readQuote(it, karat)
                        } catch (_: InvalidRatePageException) {
                            GoldFetchResult.Failure(RateFetchError.InvalidResponse)
                        } catch (error: IOException) {
                            failure(error)
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    private fun readQuote(response: Response, karat: GoldKarat): GoldFetchResult {
        if (!response.isSuccessful) {
            return GoldFetchResult.Failure(
                RateFetchError.Http(response.code, response.header("Retry-After")?.take(128))
            )
        }
        val body = response.body
        val mediaType = body.contentType()
        if (mediaType?.type != "text" || mediaType.subtype != "html") {
            return GoldFetchResult.Failure(RateFetchError.InvalidResponse)
        }
        val source = body.source()
        source.request(MAX_PAGE_BYTES + 1)
        if (source.buffer.size > MAX_PAGE_BYTES) {
            return GoldFetchResult.Failure(RateFetchError.InvalidResponse)
        }
        return GoldFetchResult.Success(parser.parse(body.string(), karat, clock.instant()))
    }

    private fun failure(error: IOException) = GoldFetchResult.Failure(
        if (error is InterruptedIOException) RateFetchError.Timeout else RateFetchError.Network
    )

    private companion object {
        const val MAX_PAGE_BYTES = 512L * 1024
    }
}
