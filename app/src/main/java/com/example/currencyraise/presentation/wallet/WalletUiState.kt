package com.example.currencyraise.presentation.wallet

import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRate
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
    val goldInput: String = "",
    val goldKarat: GoldKarat = GoldKarat.K21,
    val loadingSavings: Boolean = true,
    val savingsReadFailed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saveConfirmed: Boolean = false,
    val rate: ExchangeRate? = null,
    val goldRate: GoldRate? = null,
    val history: List<RateObservation> = emptyList(),
    val goldHistory: List<RateObservation> = emptyList(),
    val loadingRate: Boolean = true,
    val rateReadFailed: Boolean = false,
    val loadingHistory: Boolean = true,
    val historyReadFailed: Boolean = false,
    val loadingGoldRate: Boolean = true,
    val goldRateReadFailed: Boolean = false,
    val loadingGoldHistory: Boolean = true,
    val goldHistoryReadFailed: Boolean = false,
    val refreshingRate: Boolean = false,
    val refreshFailed: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val intervalMinutes: Int = 60,
) {
    val draftBalance: SavingsBalance?
        get() {
            val usd = usdInput.toSavingsAmount() ?: return null
            val egp = egpInput.toSavingsAmount() ?: return null
            val gold = goldInput.toSavingsAmount() ?: return null
            return SavingsBalance(usd, egp, gold, goldKarat)
        }

    val hasChanges: Boolean
        get() = draftBalance?.let { draft ->
            draft.usd.compareTo(savedBalance.usd) != 0 ||
                draft.egp.compareTo(savedBalance.egp) != 0 ||
                draft.goldGrams.compareTo(savedBalance.goldGrams) != 0 ||
                draft.goldKarat != savedBalance.goldKarat
        } ?: false

    val canSave: Boolean
        get() = !loadingSavings && !savingsReadFailed && !saving && draftBalance != null && hasChanges

    val valuation: SavingsValuation?
        get() = savedBalance.valueAt(rate, goldRate)

    val needsBankRate: Boolean
        get() = savedBalance.usd.signum() > 0

    val needsGoldRate: Boolean
        get() = savedBalance.goldGrams.signum() > 0
}

private fun String.toSavingsAmount(): BigDecimal? = when {
    isBlank() -> BigDecimal.ZERO
    this == "." -> null
    else -> toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
}
