package com.example.currencyraise.data.local

import com.example.currencyraise.domain.model.RateObservation
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** Bounded history for the 24-hour/7-day charts; saved in the quote's own DataStore transaction. */
internal object RateHistory {
    const val MAX_OBSERVATIONS = 2048
    val RETENTION: Duration = Duration.ofDays(8)
    private val decimal = Regex("[0-9]{1,9}(\\.[0-9]{1,9})?")

    fun append(history: List<RateObservation>, point: RateObservation): List<RateObservation> {
        // Device clock corrections must not leave the chart out of order. A repeated timestamp
        // represents one observation position, with the most recently fetched prices winning.
        val ordered = (history + point).associateBy { it.observedAt }.values.sortedBy { it.observedAt }
        val cutoff = ordered.last().observedAt.minus(RETENTION)
        return ordered.filter { it.observedAt >= cutoff }.takeLast(MAX_OBSERVATIONS)
    }

    fun encode(history: List<RateObservation>): String = history.joinToString("\n") {
        "${it.observedAt}|${it.buyRate.toPlainString()}|${it.sellRate.toPlainString()}"
    }

    fun decode(value: String): List<RateObservation> {
        try {
            require(value.length <= MAX_OBSERVATIONS * 96)
            if (value.isEmpty()) return emptyList()
            val lines = value.split('\n')
            require(lines.size <= MAX_OBSERVATIONS)
            return lines.map { line ->
                val fields = line.split('|')
                require(fields.size == 3 && fields[0].length <= 40)
                require(decimal.matches(fields[1]) && decimal.matches(fields[2]))
                RateObservation(Instant.parse(fields[0]), fields[1].toBigDecimal(), fields[2].toBigDecimal())
            }.also { points ->
                require(points.zipWithNext().all { (a, b) -> a.observedAt < b.observedAt })
            }
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid saved rate history", error)
        } catch (error: DateTimeParseException) {
            throw IOException("Invalid saved history timestamp", error)
        }
    }
}
