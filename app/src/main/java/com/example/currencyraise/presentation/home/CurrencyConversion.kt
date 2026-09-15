package com.example.currencyraise.presentation.home

import com.example.currencyraise.domain.model.ExchangeRate
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal enum class ConversionDirection {
    USD_TO_EGP,
    EGP_TO_USD,
}

internal data class ConversionResult(
    val amount: BigDecimal,
    val appliedRate: BigDecimal,
)

internal fun convertCurrency(
    amount: BigDecimal,
    rate: ExchangeRate,
    direction: ConversionDirection,
): ConversionResult? {
    if (amount.signum() < 0 || rate.baseCurrency != "USD" || rate.quoteCurrency != "EGP") return null

    return when (direction) {
        ConversionDirection.USD_TO_EGP -> ConversionResult(
            amount = amount.multiply(rate.buyRate),
            appliedRate = rate.buyRate,
        )
        ConversionDirection.EGP_TO_USD -> ConversionResult(
            amount = amount.divide(rate.sellRate, MathContext.DECIMAL128),
            appliedRate = rate.sellRate,
        )
    }
}

internal fun normalizeCurrencyInput(raw: String): String {
    val normalized = buildString {
        var hasDecimalSeparator = false
        var integerDigits = 0
        var fractionDigits = 0

        raw.forEach { character ->
            val digit = Character.digit(character, 10)
            when {
                digit >= 0 && !hasDecimalSeparator && integerDigits < 15 -> {
                    append(digit)
                    integerDigits++
                }
                digit >= 0 && hasDecimalSeparator && fractionDigits < 2 -> {
                    append(digit)
                    fractionDigits++
                }
                character in decimalSeparators && !hasDecimalSeparator -> {
                    if (isEmpty()) append('0')
                    append('.')
                    hasDecimalSeparator = true
                }
            }
        }
    }
    return normalized
}

internal fun parseCurrencyAmount(input: String): BigDecimal? =
    input.takeIf { it.isNotBlank() && it != "." }?.toBigDecimalOrNull()

internal fun formatCurrencyAmount(value: BigDecimal, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = true
    }.format(value)

private val decimalSeparators = setOf('.', ',', '\u066B')
