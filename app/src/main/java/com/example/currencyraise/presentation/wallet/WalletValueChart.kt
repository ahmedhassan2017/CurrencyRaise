package com.example.currencyraise.presentation.wallet

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
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.presentation.components.CurrencyPanel
import com.example.currencyraise.presentation.home.ChartPeriod
import com.example.currencyraise.presentation.home.formatCurrencyAmount
import com.example.currencyraise.presentation.home.formatFetchTime
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun WalletValueChart(
    balance: SavingsBalance,
    history: List<RateObservation>,
    now: Instant,
    locale: Locale,
    zone: ZoneId,
    intervalHours: Int,
    loading: Boolean,
    readFailed: Boolean,
) {
    var period by rememberSaveable { mutableStateOf(ChartPeriod.DAILY) }
    val data = remember(balance, history, period, now) {
        walletChartData(balance, history, period, now)
    }

    CurrencyPanel {
        Text(
            stringResource(R.string.wallet_chart_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.wallet_chart_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChartPeriod.entries.forEach { choice ->
                FilterChip(
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
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }

        when {
            balance.usd.signum() == 0 && balance.egp.signum() == 0 ->
                Text(stringResource(R.string.wallet_chart_empty_savings))
            readFailed -> Text(
                stringResource(R.string.wallet_chart_read_failed),
                color = MaterialTheme.colorScheme.error,
            )
            loading -> Text(stringResource(R.string.wallet_chart_loading))
            data.points.isEmpty() -> Text(stringResource(R.string.wallet_chart_empty_history))
            else -> WalletChartContent(
                data = data,
                period = period,
                locale = locale,
                zone = zone,
                maximumGap = Duration.ofHours(intervalHours.coerceAtLeast(1) * 2L),
            )
        }
        Text(
            stringResource(R.string.wallet_chart_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WalletChartContent(
    data: WalletChartData,
    period: ChartPeriod,
    locale: Locale,
    zone: ZoneId,
    maximumGap: Duration,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    var selectedIndex by remember(data.points) { mutableIntStateOf(data.points.lastIndex) }
    val selected = data.points[selectedIndex]
    val segments = remember(data.points, maximumGap) { data.points.contiguousSegments(maximumGap) }
    val periodLabel = stringResource(
        if (period == ChartPeriod.DAILY) R.string.history_24_hours else R.string.history_7_days,
    )
    val summary = pluralStringResource(
        R.plurals.wallet_chart_description,
        data.points.size,
        data.points.size,
        formatCurrencyAmount(data.points.first().totalEgp, locale),
        formatCurrencyAmount(data.points.last().totalEgp, locale),
    )
    val middle = remember(data.minimum, data.maximum) {
        data.minimum.add(data.maximum).divide(BigDecimal("2"), MathContext.DECIMAL64)
    }

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
            Text(stringResource(R.string.wallet_chart_latest), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(
                    R.string.wallet_chart_value,
                    formatCurrencyAmount(data.points.last().totalEgp, locale),
                ),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            data.change?.let { change ->
                Text(
                    walletChange(change, periodLabel, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    Text(
        stringResource(R.string.wallet_chart_axis),
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
                Text(formatCurrencyAmount(value, locale), style = MaterialTheme.typography.labelSmall)
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(data) {
                    detectTapGestures { tap ->
                        val inset = 8.dp.toPx()
                        val fraction = ((tap.x - inset) / (size.width - inset * 2).coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        selectedIndex = data.points.indices.minBy {
                            abs(data.x(data.points[it].observedAt) - fraction)
                        }
                    }
                }
                .semantics { contentDescription = summary },
        ) {
            val inset = 8.dp.toPx()
            val plotBottom = size.height - inset
            fun position(point: WalletValuePoint) = Offset(
                x = inset + data.x(point.observedAt) * (size.width - inset * 2),
                y = inset + data.y(point.totalEgp) * (size.height - inset * 2),
            )

            repeat(5) { grid ->
                val y = inset + (size.height - inset * 2) * grid / 4
                drawLine(
                    outline,
                    Offset(inset, y),
                    Offset(size.width - inset, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            segments.forEach { segment ->
                val first = position(segment.first())
                val last = position(segment.last())
                val area = Path().apply {
                    moveTo(first.x, plotBottom)
                    lineTo(first.x, first.y)
                    segment.drop(1).forEach { point ->
                        val position = position(point)
                        lineTo(position.x, position.y)
                    }
                    lineTo(last.x, plotBottom)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0.02f)),
                        startY = inset,
                        endY = plotBottom,
                    ),
                )
                val line = Path().apply {
                    moveTo(first.x, first.y)
                    segment.drop(1).forEach { point ->
                        val position = position(point)
                        lineTo(position.x, position.y)
                    }
                }
                drawPath(
                    line,
                    lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            listOf(0, data.points.lastIndex).distinct().forEach { index ->
                drawCircle(lineColor, 3.5.dp.toPx(), position(data.points[index]))
            }
            val selectedPosition = position(selected)
            drawLine(
                lineColor.copy(alpha = 0.55f),
                Offset(selectedPosition.x, inset),
                Offset(selectedPosition.x, plotBottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            drawCircle(surface, 7.dp.toPx(), selectedPosition)
            drawCircle(lineColor, 4.5.dp.toPx(), selectedPosition)
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
                Text(stringResource(R.string.wallet_chart_selected), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(
                        R.string.wallet_chart_value,
                        formatCurrencyAmount(selected.totalEgp, locale),
                    ),
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
        ) {
            TextButton(
                onClick = { selectedIndex-- },
                enabled = selectedIndex > 0,
                shape = MaterialTheme.shapes.small,
            ) { Text(stringResource(R.string.history_previous)) }
            TextButton(
                onClick = { selectedIndex++ },
                enabled = selectedIndex < data.points.lastIndex,
                shape = MaterialTheme.shapes.small,
            ) { Text(stringResource(R.string.history_next)) }
        }
    } else {
        Text(stringResource(R.string.wallet_chart_single), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun walletChange(change: BigDecimal, periodLabel: String, locale: Locale): String =
    stringResource(
        when (change.signum()) {
            1 -> R.string.wallet_chart_change_up
            -1 -> R.string.wallet_chart_change_down
            else -> R.string.wallet_chart_change_unchanged
        },
        formatCurrencyAmount(change.abs(), locale),
        periodLabel,
    )

private fun List<WalletValuePoint>.contiguousSegments(maximumGap: Duration): List<List<WalletValuePoint>> {
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
