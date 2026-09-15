package com.example.currencyraise.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.presentation.components.CurrencyPanel
import java.util.Locale

@Composable
internal fun CurrencyCalculator(
    bank: Bank,
    rate: ExchangeRate?,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    var amountInput by rememberSaveable { mutableStateOf("") }
    var directionName by rememberSaveable { mutableStateOf(ConversionDirection.USD_TO_EGP.name) }
    val direction = remember(directionName) {
        ConversionDirection.entries.firstOrNull { it.name == directionName }
            ?: ConversionDirection.USD_TO_EGP
    }
    val amount = remember(amountInput) { parseCurrencyAmount(amountInput) }
    val conversion = remember(amount, rate, direction) {
        amount?.let { value -> rate?.let { convertCurrency(value, it, direction) } }
    }
    val bankName = stringResource(
        if (bank == Bank.CIB) R.string.bank_cib else R.string.bank_banque_misr,
    )
    val sourceCurrency = if (direction == ConversionDirection.USD_TO_EGP) "USD" else "EGP"
    val resultCurrency = if (direction == ConversionDirection.USD_TO_EGP) "EGP" else "USD"

    CurrencyPanel(modifier = modifier) {
        Text(
            text = stringResource(R.string.calculator_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.calculator_description, bankName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConversionDirection.entries.forEach { option ->
                FilterChip(
                    selected = direction == option,
                    onClick = { directionName = option.name },
                    label = {
                        Text(
                            stringResource(
                                if (option == ConversionDirection.USD_TO_EGP) {
                                    R.string.calculator_usd_to_egp
                                } else {
                                    R.string.calculator_egp_to_usd
                                },
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }

        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = normalizeCurrencyInput(it) },
            label = { Text(stringResource(R.string.calculator_amount)) },
            suffix = { Text(sourceCurrency) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            rate == null -> Text(
                text = stringResource(R.string.calculator_waiting_rate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            conversion != null -> {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.calculator_result),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "${formatCurrencyAmount(conversion.amount, locale)} $resultCurrency",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = stringResource(
                        if (direction == ConversionDirection.USD_TO_EGP) {
                            R.string.calculator_buy_rate_note
                        } else {
                            R.string.calculator_sell_rate_note
                        },
                        bankName,
                        formatRate(conversion.appliedRate, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.calculator_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
