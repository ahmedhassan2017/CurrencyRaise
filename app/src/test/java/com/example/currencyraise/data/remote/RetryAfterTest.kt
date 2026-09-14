package com.example.currencyraise.data.remote

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RetryAfterTest {
    private val now = Instant.parse("2026-09-11T10:00:00Z")
    @Test fun deltaSeconds() { assertEquals(now.plusSeconds(120), retryAfterDeadline(" 120 ", now)) }
    @Test fun httpDate() { assertEquals(now.plusSeconds(120), retryAfterDeadline("Fri, 11 Sep 2026 10:02:00 GMT", now)) }
    @Test fun invalidOrExpiredValueIsIgnored() {
        for (value in listOf(null, "", " ", "-1", "0", "1.5", "nonsense", "Thu, 10 Sep 2026 10:00:00 GMT"))
            assertNull(retryAfterDeadline(value, now))
    }
    @Test fun overflowNeverShortensProviderDeadline() {
        assertEquals(Instant.MAX, retryAfterDeadline("999999999999999999999999", now))
        assertEquals(Instant.MAX, retryAfterDeadline(Long.MAX_VALUE.toString(), now))
    }
}
