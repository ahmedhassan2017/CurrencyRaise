package com.example.currencyraise.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.presentation.components.CurrencyPanel
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val buyColor = MaterialTheme.colorScheme.primary
    val sellColor = MaterialTheme.colorScheme.secondary
    CurrencyPanel {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartPeriod.entries.forEach { choice ->
                FilterChip(
                    shape = MaterialTheme.shapes.small,
                    selected = period == choice, onClick = { period = choice },
                    label = { Text(stringResource(if (choice == ChartPeriod.DAILY) R.string.history_daily else R.string.history_weekly)) },
                )
            }
        }
        Text(
            stringResource(if (period == ChartPeriod.DAILY) R.string.history_24_hours else R.string.history_7_days),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            readFailed -> {
                Text(stringResource(R.string.history_read_failed), color = MaterialTheme.colorScheme.error)
                TextButton(shape = MaterialTheme.shapes.small, onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
            }
            loading -> Text(stringResource(R.string.history_loading))
            data.points.isEmpty() -> Text(stringResource(R.string.history_empty))
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Legend(stringResource(R.string.bank_buys), buyColor, dashed = false)
                    Legend(stringResource(R.string.bank_sells), sellColor, dashed = true)
                }
                ChartPlot(data, buyColor, sellColor, locale, zone, period, Duration.ofHours(intervalHours.coerceAtLeast(1) * 2L))
                if (data.points.size == 1) {
                    Text(stringResource(R.string.history_single), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.history_change_label), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.history_buy_change, signedChange(data.buyChange!!, locale)),
                            color = buyColor, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.history_sell_change, signedChange(data.sellChange!!, locale)),
                            color = sellColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    pluralStringResource(R.plurals.history_coverage, data.points.size, data.points.size,
                        formatFetchTime(data.points.first().observedAt, locale, zone),
                        formatFetchTime(data.points.last().observedAt, locale, zone)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(stringResource(R.string.history_observed_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(24.dp, 12.dp)) {
            drawLine(color, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChartPlot(
    data: RateChartData, buyColor: Color, sellColor: Color, locale: Locale,
    zone: ZoneId, period: ChartPeriod, maximumGap: Duration,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    var selectedIndex by remember(data.points) { mutableIntStateOf(data.points.lastIndex) }
    val selected = data.points[selectedIndex]
    val summary = pluralStringResource(R.plurals.history_chart_description, data.points.size, data.points.size,
        formatRate(data.points.first().buyRate, locale), formatRate(data.points.last().buyRate, locale),
        formatRate(data.points.first().sellRate, locale), formatRate(data.points.last().sellRate, locale))
    val formatter = remember(locale, zone, period) {
        DateTimeFormatter.ofPattern(if (period == ChartPeriod.DAILY) "HH:mm" else "EEE d", locale).withZone(zone)
    }
    Text(stringResource(R.string.history_axis_unit), style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(formatRate(data.maximum, locale), style = MaterialTheme.typography.labelSmall)
            Text(formatRate(data.minimum, locale), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()
            .pointerInput(data) {
                detectTapGestures { tap ->
                    val inset = 5.dp.toPx()
                    val tappedX = ((tap.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    selectedIndex = data.points.indices.minBy { abs(data.x(data.points[it].observedAt) - tappedX) }
                }
            }
            .semantics { contentDescription = summary }) {
            val inset = 5.dp.toPx()
            fun position(point: RateObservation, buy: Boolean) = Offset(
                inset + data.x(point.observedAt) * (size.width - inset * 2),
                inset + data.y(if (buy) point.buyRate else point.sellRate) * (size.height - inset * 2),
            )
            repeat(5) { grid ->
                val y = inset + (size.height - inset * 2) * grid / 4
                drawLine(outline, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
            }
            val selectedX = position(selected, true).x
            drawLine(outline, Offset(selectedX, inset), Offset(selectedX, size.height - inset),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            for ((buy, color) in listOf(true to buyColor, false to sellColor)) {
                data.points.zipWithNext().forEach { (a, b) ->
                    if (Duration.between(a.observedAt, b.observedAt) <= maximumGap) {
                        drawLine(color, position(a, buy), position(b, buy), strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (!buy) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
                    }
                }
                data.points.forEach { point ->
                    val position = position(point, buy)
                    if (buy) drawCircle(color, radius = 3.dp.toPx(), center = position)
                    else drawRect(color, topLeft = position - Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 6.dp.toPx()))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatter.format(data.start), style = MaterialTheme.typography.labelSmall)
        Text(formatter.format(data.end), style = MaterialTheme.typography.labelSmall)
    }
    Text(stringResource(R.string.history_selected_check, formatFetchTime(selected.observedAt, locale, zone)),
        style = MaterialTheme.typography.labelMedium)
    Text(stringResource(R.string.history_selected_prices, formatRate(selected.buyRate, locale), formatRate(selected.sellRate, locale)),
        style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(shape = MaterialTheme.shapes.small, onClick = { selectedIndex-- }, enabled = selectedIndex > 0) {
            Text(stringResource(R.string.history_previous))
        }
        TextButton(shape = MaterialTheme.shapes.small, onClick = { selectedIndex++ }, enabled = selectedIndex < data.points.lastIndex) {
            Text(stringResource(R.string.history_next))
        }
    }
}

private fun signedChange(value: BigDecimal, locale: Locale): String =
    (if (value.signum() > 0) "+" else "") + formatRate(value, locale)
