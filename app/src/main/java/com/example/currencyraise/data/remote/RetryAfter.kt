package com.example.currencyraise.data.remote

import java.time.DateTimeException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun retryAfterDeadline(value: String?, now: Instant): Instant? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (text.all { it in '0'..'9' }) {
        val seconds = text.toLongOrNull() ?: return Instant.MAX
        if (seconds == 0L) return null
        return try { now.plusSeconds(seconds) } catch (_: DateTimeException) { Instant.MAX }
            catch (_: ArithmeticException) { Instant.MAX }
    }
    return try {
        ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().takeIf { it > now }
    } catch (_: DateTimeException) { null }
}
