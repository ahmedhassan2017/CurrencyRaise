package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.model.RateFetchResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BanqueMisrRemoteSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var remote: BanqueMisrRemoteSource
    private lateinit var lastCall: Call
    private val fetched = Instant.parse("2026-09-10T20:00:00Z")

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.SECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        val factory = object : Call.Factory {
            override fun newCall(request: Request): Call {
                assertEquals("https", request.url.scheme)
                assertEquals("www.banquemisr.com", request.url.host)
                return client.newCall(request.newBuilder().url(server.url("/rates")).build())
                    .also { lastCall = it }
            }
        }
        remote = BanqueMisrRemoteSource(factory, BanqueMisrParser(), Clock.fixed(fetched, ZoneOffset.UTC))
    }

    @After fun tearDown() {
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private fun html(body: String) = MockResponse.Builder()
        .addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()
    private fun fixture() = checkNotNull(javaClass.getResource("/banque-misr/usd-cash.html")).readText()

    @Test fun fetchesAndMapsUsingInjectedClock() = runBlocking {
        server.enqueue(html(fixture()))
        val result = remote.fetchLatest() as RateFetchResult.Success
        assertEquals(fetched, result.rate.fetchedAt)
        assertEquals("51.27", result.rate.buyRate.toPlainString())
        assertEquals("text/html", server.takeRequest().headers["Accept"])
    }
    @Test fun retainsRateLimitAndRetryAfter() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "120").build())
        assertEquals(RateFetchResult.Failure(RateFetchError.Http(429, "120")), remote.fetchLatest())
    }
    @Test fun classifiesServerFailure() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(RateFetchResult.Failure(RateFetchError.Http(503)), remote.fetchLatest())
    }
    @Test fun rejectsRedirectInsteadOfFollowingLoginPage() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/login").build())
        assertEquals(RateFetchResult.Failure(RateFetchError.Http(302)), remote.fetchLatest())
        assertEquals(1, server.requestCount)
    }
    @Test fun rejectsHttp200ErrorPage() = runBlocking {
        server.enqueue(html("<html><h1>Request Rejected</h1></html>"))
        assertEquals(RateFetchResult.Failure(RateFetchError.InvalidResponse), remote.fetchLatest())
    }
    @Test fun rejectsNonHtml() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body("{}").build())
        assertEquals(RateFetchResult.Failure(RateFetchError.InvalidResponse), remote.fetchLatest())
    }
    @Test fun rejectsOversizedBodyWithoutContentLength() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html")
            .chunkedBody("x".repeat(512 * 1024 + 1), 8192).build())
        assertEquals(RateFetchResult.Failure(RateFetchError.InvalidResponse), remote.fetchLatest())
    }
    @Test fun requestTimeoutHasItsOwnCategory() = runBlocking {
        server.enqueue(MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).build())
        assertEquals(RateFetchResult.Failure(RateFetchError.Timeout), remote.fetchLatest())
    }
    @Test fun cancellationCancelsTheHttpCall() = runBlocking {
        server.enqueue(MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).build())
        val job = async { remote.fetchLatest() }
        withTimeout(5_000) {
            val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
            assertNotNull(request)
            job.cancelAndJoin()
        }
        assertTrue(lastCall.isCanceled())
        assertTrue(job.isCancelled)
    }
}
