package com.example.currencyraise.presentation.home

import android.content.ActivityNotFoundException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RateChange
import com.example.currencyraise.presentation.background.BackgroundStatusRoute
import com.example.currencyraise.presentation.background.BackgroundStatusSection
import com.example.currencyraise.presentation.components.CurrencyPanel
import com.example.currencyraise.presentation.components.CurrencyRaiseHeader
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
    HomeScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        backgroundStatus = { BackgroundStatusRoute() },
        onOpenSource = { url ->
            try {
                require(url.toUri().scheme == "https")
                handler.openUri(url)
            } catch (_: ActivityNotFoundException) {
                linkFailed = true
            } catch (_: IllegalArgumentException) {
                linkFailed = true
            }
        },
    )
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
    backgroundStatus: @Composable () -> Unit = { BackgroundStatusSection() },
) {
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.US
    val zone = ZoneId.systemDefault()
    var storageHelp by rememberSaveable { mutableStateOf(false) }
    val sourceUrl = state.rate?.sourceUrl ?: stringResource(R.string.bank_url)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
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

            RateHero(state.rate, locale)

            if (state.rateReadFailed || state.settingsReadFailed) {
                Notice(
                    stringResource(if (state.rateReadFailed) R.string.cache_read_failed else R.string.settings_read_failed),
                    isError = true,
                )
                TextButton(onClick = { storageHelp = true }) { Text(stringResource(R.string.storage_help)) }
            }
            state.refreshError?.takeUnless { it == HomeError.STORAGE_READ && state.rateReadFailed }?.let { error ->
                Notice(stringResource(error.messageResource()), isError = true)
                if ((error == HomeError.STORAGE_READ || error == HomeError.STORAGE_WRITE) &&
                    !state.rateReadFailed && !state.settingsReadFailed
                ) {
                    TextButton(onClick = { storageHelp = true }) { Text(stringResource(R.string.storage_help)) }
                }
            }

            if (state.rate == null) {
                CurrencyPanel {
                    Text(
                        stringResource(if (state.loadingCache || state.refreshing) R.string.loading_first else R.string.no_saved_rates),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(
                            if (state.loadingCache) R.string.reading_saved
                            else if (state.refreshing) R.string.fetching_first
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
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    stringResource(
                        if (state.refreshing) R.string.checking
                        else if (state.refreshError != null || state.rateReadFailed || state.settingsReadFailed) R.string.try_again
                        else R.string.refresh_rates,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            state.rate?.let { rate ->
                if (state.refreshError != null || state.rateReadFailed) {
                    Notice(stringResource(R.string.showing_saved))
                }
                FreshnessPill(
                    stringResource(
                        when (state.freshness) {
                            Freshness.RECENT_CHECK -> R.string.freshness_recent
                            Freshness.CHECK_DUE -> R.string.freshness_due
                            Freshness.UNKNOWN -> R.string.freshness_unknown
                        },
                    ),
                )
                CurrencyPanel {
                    Metadata(
                        stringResource(R.string.last_checked),
                        formatFetchTime(rate.fetchedAt, locale, zone),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Metadata(
                        stringResource(R.string.source_time),
                        rate.sourceDisplayedAt?.let { formatSourceTime(it, locale) }
                            ?: stringResource(R.string.not_supplied),
                    )
                    if (rate.sourceDisplayedAt != null) {
                        Text(
                            stringResource(R.string.source_timezone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

            CurrencyPanel(containerColor = MaterialTheme.colorScheme.surface) {
                backgroundStatus()
            }

            OutlinedButton(
                onClick = { onOpenSource(sourceUrl) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
            ) {
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
private fun RateHero(rate: ExchangeRate?, locale: Locale) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                    ),
                ),
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                ) {
                    Text(
                        stringResource(
                            R.string.source_cash,
                            rate?.sourceName ?: stringResource(R.string.bank_name),
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    stringResource(R.string.currency_pair),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.rate_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rate?.let {
                    Spacer(Modifier.height(2.dp))
                    RatePair(it, locale)
                    Text(
                        stringResource(R.string.cash_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
                RateCard(stringResource(R.string.bank_buys), buy, stringResource(R.string.you_sell), true, Modifier.fillMaxWidth())
                RateCard(stringResource(R.string.bank_sells), sell, stringResource(R.string.you_buy), false, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RateCard(stringResource(R.string.bank_buys), buy, stringResource(R.string.you_sell), true, Modifier.weight(1f))
                RateCard(stringResource(R.string.bank_sells), sell, stringResource(R.string.you_buy), false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RateCard(
    label: String,
    value: String,
    explanation: String,
    emphasized: Boolean,
    modifier: Modifier,
) {
    val container = if (emphasized) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun FreshnessPill(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Spacer(Modifier.width(9.dp))
            Text(message, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Metadata(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Notice(message: String, isError: Boolean = false) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, content.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun HomeError.messageResource(): Int = when (this) {
    HomeError.DEFERRED -> R.string.error_deferred
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
