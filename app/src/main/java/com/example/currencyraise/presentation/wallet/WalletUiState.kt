package com.example.currencyraise.presentation.wallet

import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.domain.model.SavingsValuation
import com.example.currencyraise.domain.model.valueAt
import java.math.BigDecimal
import java.time.Instant

data class WalletUiState(
    val bank: Bank = Bank.CIB,
    val banks: List<Bank> = Bank.entries,
    val savedBalance: SavingsBalance = SavingsBalance.EMPTY,
    val usdInput: String = "",
    val egpInput: String = "",
    val loadingSavings: Boolean = true,
    val savingsReadFailed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saveConfirmed: Boolean = false,
    val rate: ExchangeRate? = null,
    val history: List<RateObservation> = emptyList(),
    val loadingRate: Boolean = true,
    val rateReadFailed: Boolean = false,
    val loadingHistory: Boolean = true,
    val historyReadFailed: Boolean = false,
    val refreshingRate: Boolean = false,
    val refreshFailed: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val intervalHours: Int = 1,
) {
    val draftBalance: SavingsBalance?
        get() {
            val usd = usdInput.toSavingsAmount() ?: return null
            val egp = egpInput.toSavingsAmount() ?: return null
            return SavingsBalance(usd, egp)
        }

    val hasChanges: Boolean
        get() = draftBalance?.let { draft ->
            draft.usd.compareTo(savedBalance.usd) != 0 || draft.egp.compareTo(savedBalance.egp) != 0
        } ?: false

    val canSave: Boolean
        get() = !loadingSavings && !savingsReadFailed && !saving && draftBalance != null && hasChanges

    val valuation: SavingsValuation?
        get() = rate?.let(savedBalance::valueAt)
}

private fun String.toSavingsAmount(): BigDecimal? = when {
    isBlank() -> BigDecimal.ZERO
    this == "." -> null
    else -> toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
}
