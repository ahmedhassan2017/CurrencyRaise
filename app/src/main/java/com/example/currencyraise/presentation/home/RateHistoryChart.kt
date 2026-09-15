package com.example.currencyraise.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.presentation.components.CurrencyPanel
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun RateHistoryChart(
    history: List<RateObservation>,
    now: Instant,
    locale: Locale,
    zone: ZoneId,
    intervalHours: Int,
    loading: Boolean,
    readFailed: Boolean,
    onRetry: () -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(ChartPeriod.DAILY) }
    val data = remember(history, period, now) { rateChartData(history, period, now) }
    val lineColor = MaterialTheme.colorScheme.primary

    CurrencyPanel {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.history_buy_rate_only),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChartPeriod.entries.forEach { choice ->
                FilterChip(
                    shape = MaterialTheme.shapes.small,
                    selected = period == choice,
                    onClick = { period = choice },
                    label = {
                        Text(
                            stringResource(
                                if (choice == ChartPeriod.DAILY) R.string.history_daily
                                else R.string.history_weekly,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }

        when {
            readFailed -> {
                Text(stringResource(R.string.history_read_failed), color = MaterialTheme.colorScheme.error)
                TextButton(shape = MaterialTheme.shapes.small, onClick = onRetry) {
                    Text(stringResource(R.string.history_retry))
                }
            }
            loading -> Text(stringResource(R.string.history_loading))
            data.points.isEmpty() -> Text(stringResource(R.string.history_empty))
            else -> {
                HistorySummary(data, period, locale)
                ChartPlot(
                    data = data,
                    lineColor = lineColor,
                    locale = locale,
                    zone = zone,
                    period = period,
                    maximumGap = Duration.ofHours(intervalHours.coerceAtLeast(1) * 2L),
                )
                if (data.points.size == 1) {
                    Text(stringResource(R.string.history_single), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    pluralStringResource(
                        R.plurals.history_coverage,
                        data.points.size,
                        data.points.size,
                        formatFetchTime(data.points.first().observedAt, locale, zone),
                        formatFetchTime(data.points.last().observedAt, locale, zone),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            text = stringResource(R.string.history_observed_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySummary(data: RateChartData, period: ChartPeriod, locale: Locale) {
    val latest = data.points.last()
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.history_latest_buy),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.history_rate_value, formatRate(latest.buyRate, locale)),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            data.buyChange?.let { change ->
                Text(
                    text = periodChange(change, period, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun periodChange(change: BigDecimal, period: ChartPeriod, locale: Locale): String {
    val periodLabel = stringResource(
        if (period == ChartPeriod.DAILY) R.string.history_24_hours else R.string.history_7_days,
    )
    return stringResource(
        when (change.signum()) {
            1 -> R.string.history_change_up
            -1 -> R.string.history_change_down
            else -> R.string.history_change_unchanged
        },
        formatRate(change.abs(), locale),
        periodLabel,
    )
}

@Composable
private fun ChartPlot(
    data: RateChartData,
    lineColor: Color,
    locale: Locale,
    zone: ZoneId,
    period: ChartPeriod,
    maximumGap: Duration,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    var selectedIndex by remember(data.points) { mutableIntStateOf(data.points.lastIndex) }
    val selected = data.points[selectedIndex]
    val segments = remember(data.points, maximumGap) { data.points.contiguousSegments(maximumGap) }
    val summary = pluralStringResource(
        R.plurals.history_chart_description,
        data.points.size,
        data.points.size,
        formatRate(data.points.first().buyRate, locale),
        formatRate(data.points.last().buyRate, locale),
    )
    val middle = remember(data.minimum, data.maximum) {
        data.minimum.add(data.maximum).divide(BigDecimal("2"), MathContext.DECIMAL64)
    }

    Text(
        text = stringResource(R.string.history_axis_unit),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(data.maximum, middle, data.minimum).forEach { value ->
                Text(formatRate(value, locale), style = MaterialTheme.typography.labelSmall)
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(data) {
                    detectTapGestures { tap ->
                        val inset = 8.dp.toPx()
                        val tappedX = ((tap.x - inset) / (size.width - inset * 2).coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        selectedIndex = data.points.indices.minBy {
                            abs(data.x(data.points[it].observedAt) - tappedX)
                        }
                    }
                }
                .semantics { contentDescription = summary },
        ) {
            val inset = 8.dp.toPx()
            val plotBottom = size.height - inset
            fun position(point: RateObservation) = Offset(
                x = inset + data.x(point.observedAt) * (size.width - inset * 2),
                y = inset + data.y(point.buyRate) * (size.height - inset * 2),
            )

            repeat(5) { grid ->
                val y = inset + (size.height - inset * 2) * grid / 4
                drawLine(
                    color = outline,
                    start = Offset(inset, y),
                    end = Offset(size.width - inset, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }

            segments.forEach { segment ->
                val first = position(segment.first())
                val last = position(segment.last())
                val areaPath = Path().apply {
                    moveTo(first.x, plotBottom)
                    lineTo(first.x, first.y)
                    segment.drop(1).forEach { point ->
                        val pointPosition = position(point)
                        lineTo(pointPosition.x, pointPosition.y)
                    }
                    lineTo(last.x, plotBottom)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0.02f)),
                        startY = inset,
                        endY = plotBottom,
                    ),
                )

                val linePath = Path().apply {
                    moveTo(first.x, first.y)
                    segment.drop(1).forEach { point ->
                        val pointPosition = position(point)
                        lineTo(pointPosition.x, pointPosition.y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            listOf(0, data.points.lastIndex).distinct().forEach { index ->
                drawCircle(lineColor, radius = 3.5.dp.toPx(), center = position(data.points[index]))
            }
            val selectedPosition = position(selected)
            drawLine(
                color = lineColor.copy(alpha = 0.55f),
                start = Offset(selectedPosition.x, inset),
                end = Offset(selectedPosition.x, plotBottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            drawCircle(surface, radius = 7.dp.toPx(), center = selectedPosition)
            drawCircle(lineColor, radius = 4.5.dp.toPx(), center = selectedPosition)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(
                if (period == ChartPeriod.DAILY) R.string.history_24_hours_ago
                else R.string.history_7_days_ago,
            ),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(stringResource(R.string.history_now), style = MaterialTheme.typography.labelSmall)
    }

    if (data.points.size > 1) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(stringResource(R.string.history_selected_point), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.history_rate_value, formatRate(selected.buyRate, locale)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    formatFetchTime(selected.observedAt, locale, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                shape = MaterialTheme.shapes.small,
                onClick = { selectedIndex-- },
                enabled = selectedIndex > 0,
            ) {
                Text(stringResource(R.string.history_previous))
            }
            TextButton(
                shape = MaterialTheme.shapes.small,
                onClick = { selectedIndex++ },
                enabled = selectedIndex < data.points.lastIndex,
            ) {
                Text(stringResource(R.string.history_next))
            }
        }
    }
}

private fun List<RateObservation>.contiguousSegments(maximumGap: Duration): List<List<RateObservation>> {
    if (isEmpty()) return emptyList()
    val segments = mutableListOf(mutableListOf(first()))
    drop(1).forEach { point ->
        val current = segments.last()
        if (Duration.between(current.last().observedAt, point.observedAt) > maximumGap) {
            segments += mutableListOf(point)
        } else {
            current += point
        }
    }
    return segments
}
