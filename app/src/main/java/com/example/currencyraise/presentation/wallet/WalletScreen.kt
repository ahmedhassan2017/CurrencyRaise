package com.example.currencyraise.presentation.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.presentation.components.CurrencyPanel
import com.example.currencyraise.presentation.components.CurrencyRaiseHeader
import com.example.currencyraise.presentation.home.formatCurrencyAmount
import com.example.currencyraise.presentation.home.formatFetchTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun WalletRoute(
    viewModel: WalletViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    WalletScreen(
        state = state,
        onOpenSettings = onOpenSettings,
        onSelectBank = viewModel::selectBank,
        onUsdInput = viewModel::updateUsdInput,
        onEgpInput = viewModel::updateEgpInput,
        onSave = viewModel::save,
        onRetrySavings = viewModel::retrySavings,
        onRefreshRate = viewModel::refreshRate,
        modifier = modifier,
    )
}

@Composable
fun WalletScreen(
    state: WalletUiState,
    onOpenSettings: () -> Unit,
    onSelectBank: (Bank) -> Unit,
    onUsdInput: (String) -> Unit,
    onEgpInput: (String) -> Unit,
    onSave: () -> Unit,
    onRetrySavings: () -> Unit,
    onRefreshRate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.US
    val zone = ZoneId.systemDefault()
    val bankName = stringResource(
        if (state.bank == Bank.CIB) R.string.bank_cib else R.string.bank_banque_misr,
    )

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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.wallet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.wallet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(stringResource(R.string.select_bank), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.banks.forEach { bank ->
                    FilterChip(
                        selected = state.bank == bank,
                        onClick = { onSelectBank(bank) },
                        label = {
                            Text(
                                stringResource(
                                    if (bank == Bank.CIB) R.string.bank_cib
                                    else R.string.bank_banque_misr,
                                ),
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }

            WalletBalanceHero(state, locale)
            RateStatus(state, bankName, locale, zone, onRefreshRate)
            SavingsEditor(
                state = state,
                onUsdInput = onUsdInput,
                onEgpInput = onEgpInput,
                onSave = onSave,
                onRetry = onRetrySavings,
            )
            WalletValueChart(
                balance = state.savedBalance,
                history = state.history,
                now = state.now,
                locale = locale,
                zone = zone,
                intervalHours = state.intervalHours,
                loading = state.loadingHistory,
                readFailed = state.historyReadFailed,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WalletBalanceHero(state: WalletUiState, locale: Locale) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.valuation?.let { valuation ->
                BalanceValue(
                    label = stringResource(R.string.wallet_total_egp),
                    value = "${formatCurrencyAmount(valuation.totalEgp, locale)} EGP",
                )
                Text(
                    stringResource(R.string.wallet_valuation_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: Text(
                stringResource(R.string.wallet_no_rate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BalanceValue(label: String, value: String) {
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
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RateStatus(
    state: WalletUiState,
    bankName: String,
    locale: Locale,
    zone: ZoneId,
    onRefreshRate: () -> Unit,
) {
    CurrencyPanel {
        Text(
            state.rate?.let {
                stringResource(
                    R.string.wallet_selected_bank_rate,
                    bankName,
                    formatFetchTime(it.fetchedAt, locale, zone),
                )
            } ?: stringResource(R.string.wallet_rate_unavailable, bankName),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.loadingRate || state.refreshingRate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.rateReadFailed || state.refreshFailed) {
            Text(
                stringResource(R.string.wallet_refresh_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(
            onClick = onRefreshRate,
            enabled = !state.refreshingRate,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                stringResource(
                    if (state.refreshingRate) R.string.wallet_refreshing_rate
                    else R.string.wallet_refresh_rate,
                ),
            )
        }
    }
}

@Composable
private fun SavingsEditor(
    state: WalletUiState,
    onUsdInput: (String) -> Unit,
    onEgpInput: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    CurrencyPanel {
        Text(stringResource(R.string.wallet_edit_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.wallet_edit_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loadingSavings) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.savingsReadFailed) {
            Text(
                stringResource(R.string.wallet_storage_read_failed),
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.wallet_retry))
            }
        } else {
            SavingsAmountField(
                value = state.usdInput,
                onValueChange = onUsdInput,
                label = stringResource(R.string.wallet_usd_savings),
                currency = "USD",
                imeAction = ImeAction.Next,
            )
            SavingsAmountField(
                value = state.egpInput,
                onValueChange = onEgpInput,
                label = stringResource(R.string.wallet_egp_savings),
                currency = "EGP",
                imeAction = ImeAction.Done,
            )
            if (state.draftBalance == null) {
                Text(
                    stringResource(R.string.wallet_invalid_amount),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.wallet_storage_write_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    stringResource(if (state.saving) R.string.wallet_saving else R.string.wallet_save),
                )
            }
            if (state.saveConfirmed) {
                Text(
                    stringResource(R.string.wallet_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun SavingsAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: String,
    imeAction: ImeAction,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(currency) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                val wasFocused = focused
                focused = focus.isFocused
                when {
                    focus.isFocused && !wasFocused && value.isZeroAmount() -> onValueChange("")
                    !focus.isFocused && wasFocused && value.isBlank() -> onValueChange("0")
                }
            },
    )
}

private fun String.isZeroAmount(): Boolean = toBigDecimalOrNull()?.signum() == 0
