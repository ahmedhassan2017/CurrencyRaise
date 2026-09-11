package com.example.currencyraise.presentation.home

import android.content.ActivityNotFoundException
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeRoute(viewModel: HomeViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.updateDisplayTime()
                delay(60_000)
            }
        }
    }
    val handler = LocalUriHandler.current
    var linkFailed by rememberSaveable { mutableStateOf(false) }
    HomeScreen(state, viewModel::refresh, onOpenSettings = onOpenSettings, onOpenSource = { url ->
        try {
            require(url.toUri().scheme == "https")
            handler.openUri(url)
        } catch (_: ActivityNotFoundException) {
            linkFailed = true
        } catch (_: IllegalArgumentException) {
            linkFailed = true
        }
    })
    if (linkFailed) {
        AlertDialog(
            onDismissRequest = { linkFailed = false },
            title = { Text(stringResource(R.string.source_open_failed_title)) },
            text = { Text(stringResource(R.string.source_open_failed)) },
            confirmButton = {
                TextButton(onClick = { linkFailed = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.US
    val zone = ZoneId.systemDefault()
    var storageHelp by rememberSaveable { mutableStateOf(false) }
    val sourceUrl = state.rate?.sourceUrl ?: stringResource(R.string.bank_url)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { insets ->
        Column(
            Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.source_cash, state.rate?.sourceName ?: stringResource(R.string.bank_name)),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.currency_pair), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.rate_unit), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.rateReadFailed || state.settingsReadFailed) {
                Notice(
                    stringResource(if (state.rateReadFailed) R.string.cache_read_failed else R.string.settings_read_failed),
                    isError = true,
                )
                TextButton(onClick = { storageHelp = true }) { Text(stringResource(R.string.storage_help)) }
            }
            state.refreshError?.takeUnless { it == HomeError.STORAGE_READ && state.rateReadFailed }?.let { error ->
                Notice(stringResource(error.messageResource()), isError = true)
                if (error == HomeError.STORAGE_READ || error == HomeError.STORAGE_WRITE) {
                    if (!state.rateReadFailed && !state.settingsReadFailed) {
                        TextButton(onClick = { storageHelp = true }) { Text(stringResource(R.string.storage_help)) }
                    }
                }
            }
            if (state.rate != null) {
                RatePair(state.rate, locale)
                Text(stringResource(R.string.cash_note), style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(if (state.loadingCache || state.refreshing) R.string.loading_first else R.string.no_saved_rates),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(stringResource(if (state.loadingCache) R.string.reading_saved
                            else if (state.refreshing) R.string.fetching_first else R.string.empty_help))
                    }
                }
            }
            if (state.refreshing || state.loadingCache) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Button(
                onClick = onRefresh, enabled = !state.refreshing && !state.loadingCache,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(if (state.refreshing) R.string.checking
                    else if (state.refreshError != null || state.rateReadFailed || state.settingsReadFailed) R.string.try_again
                    else R.string.refresh_rates))
            }
            if (state.rate != null) {
                if (state.refreshError != null || state.rateReadFailed) {
                    Text(stringResource(R.string.showing_saved), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(when (state.freshness) {
                        Freshness.RECENT_CHECK -> R.string.freshness_recent
                        Freshness.CHECK_DUE -> R.string.freshness_due
                        Freshness.UNKNOWN -> R.string.freshness_unknown
                    }),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Metadata(stringResource(R.string.last_checked), formatFetchTime(state.rate.fetchedAt, locale, zone))
                    Metadata(
                        stringResource(R.string.source_time),
                        state.rate.sourceDisplayedAt?.let { formatSourceTime(it, locale) }
                            ?: stringResource(R.string.not_supplied),
                    )
                    if (state.rate.sourceDisplayedAt != null) {
                        Text(stringResource(R.string.source_timezone), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val feedback = when {
                state.alreadyRefreshing -> R.string.check_already_running
                state.lastChange == RateChange.UNCHANGED -> R.string.checked_unchanged
                state.lastChange != null -> R.string.checked_saved
                else -> null
            }
            feedback?.let { Notice(stringResource(it)) }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.background_inactive), style = MaterialTheme.typography.titleSmall)
                state.settings?.let {
                    Text(
                        if (!it.automaticChecksEnabled) stringResource(R.string.automatic_disabled)
                        else pluralStringResource(R.plurals.configured_interval, it.updateInterval.hours, it.updateInterval.hours),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(stringResource(R.string.background_explanation), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onOpenSource(sourceUrl) }, modifier = Modifier.align(Alignment.Start)) {
                Text(stringResource(R.string.open_source))
            }
        }
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
private fun RatePair(rate: ExchangeRate, locale: Locale) {
    val buy = formatRate(rate.buyRate, locale)
    val sell = formatRate(rate.sellRate, locale)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        if (maxWidth < 340.dp || fontScale > 1.2f || maxOf(buy.length, sell.length) > 8) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RateCard(stringResource(R.string.bank_buys), buy, stringResource(R.string.you_sell), Modifier.fillMaxWidth())
                RateCard(stringResource(R.string.bank_sells), sell, stringResource(R.string.you_buy), Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RateCard(stringResource(R.string.bank_buys), buy, stringResource(R.string.you_sell), Modifier.weight(1f))
                RateCard(stringResource(R.string.bank_sells), sell, stringResource(R.string.you_buy), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RateCard(label: String, value: String, explanation: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Metadata(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Notice(message: String, isError: Boolean = false) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium)
    }
}

private fun HomeError.messageResource(): Int = when (this) {
    HomeError.NETWORK -> R.string.error_network
    HomeError.TIMEOUT -> R.string.error_timeout
    HomeError.PROVIDER -> R.string.error_provider
    HomeError.RATE_LIMITED -> R.string.error_rate_limited
    HomeError.STORAGE_READ -> R.string.cache_read_failed
    HomeError.STORAGE_WRITE -> R.string.error_storage_write
}

@Preview(showBackground = true)
@Composable
private fun EmptyHomePreview() {
    CurrencyRaiseTheme { HomeScreen(HomeUiState(loadingCache = false), {}, {}) }
}
