package com.example.currencyraise.presentation.gold

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.presentation.components.CurrencyPanel
import com.example.currencyraise.presentation.components.CurrencyRaiseHeader
import com.example.currencyraise.presentation.home.HomeError
import com.example.currencyraise.presentation.home.HistoryMarket
import com.example.currencyraise.presentation.home.RateHistoryChart
import com.example.currencyraise.presentation.home.formatFetchTime
import com.example.currencyraise.presentation.home.formatRate
import com.example.currencyraise.presentation.home.formatSourceTime
import com.example.currencyraise.presentation.market.MarketTab
import com.example.currencyraise.presentation.market.MarketTabs
import java.time.ZoneId
import java.util.Locale

@Composable
fun GoldScreen(
    state: GoldUiState,
    onRefresh: () -> Unit,
    onSelectKarat: (GoldKarat) -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectMarket: (MarketTab) -> Unit,
    onRetryHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.US
    val zone = ZoneId.systemDefault()
    var showDetails by rememberSaveable(state.karat) { mutableStateOf(false) }
    var showHistory by rememberSaveable(state.karat) { mutableStateOf(true) }
    var storageHelp by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .consumeWindowInsets(insets)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CurrencyRaiseHeader(
                actionLabel = stringResource(R.string.settings_title),
                onAction = onOpenSettings,
            )
            MarketTabs(MarketTab.GOLD, onSelectMarket)

            Text(stringResource(R.string.gold_select_karat), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldKarat.entries.forEach { karat ->
                    FilterChip(
                        selected = state.karat == karat,
                        onClick = { onSelectKarat(karat) },
                        label = { Text(stringResource(karat.labelResource())) },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }

            GoldHero(state, locale, zone)

            if (state.rateReadFailed) {
                GoldNotice(stringResource(R.string.gold_cache_read_failed))
                TextButton(onClick = { storageHelp = true }) { Text(stringResource(R.string.storage_help)) }
            }
            state.refreshError?.takeUnless { it == HomeError.STORAGE_READ && state.rateReadFailed }?.let {
                GoldNotice(stringResource(it.messageResource()))
                if (it == HomeError.STORAGE_READ || it == HomeError.STORAGE_WRITE) {
                    TextButton(onClick = { storageHelp = true }) { Text(stringResource(R.string.storage_help)) }
                }
            }

            if (state.rate == null) {
                CurrencyPanel {
                    Text(
                        stringResource(if (state.loadingCache || state.refreshing) R.string.gold_loading_first else R.string.gold_no_saved_rate),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(
                            if (state.loadingCache) R.string.reading_saved
                            else if (state.refreshing) R.string.gold_fetching_first
                            else R.string.empty_help,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.refreshing || state.loadingCache) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }

            Button(
                onClick = onRefresh,
                enabled = !state.refreshing && !state.loadingCache,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    stringResource(
                        if (state.refreshing) R.string.gold_checking
                        else if (state.refreshError != null || state.rateReadFailed) R.string.try_again
                        else R.string.gold_refresh,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            GoldCheckTimes(
                attemptedAt = state.lastAttemptedAt,
                successfulAt = state.rate?.fetchedAt,
                locale = locale,
                zone = zone,
            )

            val feedback = when {
                state.alreadyRefreshing -> R.string.check_already_running
                state.refreshError != null || state.rateReadFailed -> null
                state.lastChange == RateChange.UNCHANGED -> R.string.checked_unchanged
                state.lastChange != null -> R.string.checked_saved
                else -> null
            }
            feedback?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            RateHistoryChart(
                history = state.history,
                now = state.now,
                locale = locale,
                zone = zone,
                loading = state.loadingHistory,
                readFailed = state.historyReadFailed,
                onRetry = onRetryHistory,
                market = HistoryMarket.GOLD,
                expanded = showHistory,
                onToggleExpanded = { showHistory = !showHistory },
            )
            TextButton(
                onClick = { showDetails = true },
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.rate_details))
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.gold_details_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(state.rate?.sourceName ?: stringResource(R.string.gold_source_ta3weem))
                    state.rate?.let { rate ->
                        GoldMetadata(stringResource(R.string.last_checked), formatFetchTime(rate.fetchedAt, locale, zone))
                        GoldMetadata(
                            stringResource(R.string.source_time),
                            rate.sourceDisplayedAt?.let { formatSourceTime(it, locale) }
                                ?: stringResource(R.string.not_supplied),
                        )
                    }
                    Text(stringResource(R.string.gold_source_note), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        onOpenSource(state.rate?.sourceUrl ?: ta3weemUrl(state.karat))
                    }) {
                        Text(stringResource(R.string.open_source, stringResource(R.string.gold_source_ta3weem)))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (storageHelp) {
        AlertDialog(
            onDismissRequest = { storageHelp = false },
            title = { Text(stringResource(R.string.storage_help)) },
            text = { Text(stringResource(R.string.storage_recovery)) },
            confirmButton = {
                TextButton(onClick = { storageHelp = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun GoldCheckTimes(
    attemptedAt: java.time.Instant?,
    successfulAt: java.time.Instant?,
    locale: Locale,
    zone: ZoneId,
) {
    val notYet = stringResource(R.string.check_not_yet)
    Text(
        stringResource(
            R.string.last_attempted_check,
            attemptedAt?.let { formatFetchTime(it, locale, zone) } ?: notYet,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(
            R.string.last_successful_check,
            successfulAt?.let { formatFetchTime(it, locale, zone) } ?: notYet,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GoldHero(state: GoldUiState, locale: Locale, zone: ZoneId) {
    val rate = state.rate
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        rate?.sourceName ?: stringResource(R.string.gold_source_ta3weem),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    stringResource(R.string.gold_pair, stringResource(state.karat.labelResource())),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.gold_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rate?.let {
                    Spacer(Modifier.height(2.dp))
                    GoldRateCard(
                        stringResource(R.string.gold_dealer_buys),
                        formatRate(it.buyRate, locale),
                        stringResource(R.string.gold_you_sell),
                        true,
                    )
                    GoldRateCard(
                        stringResource(R.string.gold_dealer_sells),
                        formatRate(it.sellRate, locale),
                        stringResource(R.string.gold_you_buy),
                        false,
                    )
                    Text(
                        stringResource(
                            when {
                                state.refreshError != null || state.rateReadFailed -> R.string.showing_saved
                                state.freshness == GoldFreshness.CHECK_DUE -> R.string.gold_check_due
                                state.freshness == GoldFreshness.UNKNOWN -> R.string.home_check_unknown
                                else -> R.string.home_checked_at
                            },
                            formatFetchTime(it.fetchedAt, locale, zone),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoldRateCard(label: String, value: String, explanation: String, emphasized: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(explanation, style = MaterialTheme.typography.bodySmall)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GoldNotice(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GoldMetadata(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun GoldKarat.labelResource() = when (this) {
    GoldKarat.K24 -> R.string.gold_24k
    GoldKarat.K21 -> R.string.gold_21k
    GoldKarat.K18 -> R.string.gold_18k
}

private fun HomeError.messageResource() = when (this) {
    HomeError.DEFERRED -> R.string.error_deferred
    HomeError.NETWORK -> R.string.error_network
    HomeError.TIMEOUT -> R.string.error_timeout
    HomeError.PROVIDER -> R.string.error_provider
    HomeError.RATE_LIMITED -> R.string.error_rate_limited
    HomeError.STORAGE_READ -> R.string.gold_cache_read_failed
    HomeError.STORAGE_WRITE -> R.string.error_storage_write
}

private fun ta3weemUrl(karat: GoldKarat) =
    "https://ta3weem.com/en/gold-prices/${karat.ta3weemSlug}"
