package com.example.currencyraise.presentation.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.RefreshOutcome
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.domain.model.SavingsWriteResult
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.BankRateRepositories
import com.example.currencyraise.domain.repository.SavingsRepository
import com.example.currencyraise.domain.repository.SettingsRepository
import com.example.currencyraise.presentation.home.normalizeCurrencyInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val savings: SavingsRepository,
    private val rates: BankRateRepositories,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restoredBank = Bank.entries.firstOrNull {
        it.name == savedState.get<String>(BANK_KEY) && it in rates.banks
    } ?: rates.banks.first()
    private val mutableState = MutableStateFlow(
        WalletUiState(bank = restoredBank, banks = rates.banks, now = clock.instant()),
    )
    val uiState = mutableState.asStateFlow()

    private var inputsInitialized = false
    private var savingsObservation: Job? = null
    private var rateObservation: Job? = null
    private var historyObservation: Job? = null
    private var refreshJob: Job? = null
    private var settingsObservation: Job? = null
    private var bankSession = 0

    init {
        observeSavings()
        observeBankData()
        observeSettings()
    }

    fun updateUsdInput(value: String) = updateInput(usd = normalizeCurrencyInput(value))

    fun updateEgpInput(value: String) = updateInput(egp = normalizeCurrencyInput(value))

    private fun updateInput(usd: String? = null, egp: String? = null) {
        mutableState.update {
            it.copy(
                usdInput = usd ?: it.usdInput,
                egpInput = egp ?: it.egpInput,
                saveFailed = false,
                saveConfirmed = false,
            )
        }
    }

    fun save() {
        val balance = mutableState.value.draftBalance ?: return
        if (!mutableState.value.canSave) return
        mutableState.update { it.copy(saving = true, saveFailed = false, saveConfirmed = false) }
        viewModelScope.launch {
            when (savings.saveSavings(balance)) {
                SavingsWriteResult.SAVED -> mutableState.update {
                    it.copy(saving = false, saveConfirmed = true, savedBalance = balance)
                }
                SavingsWriteResult.STORAGE_FAILURE -> mutableState.update {
                    it.copy(saving = false, saveFailed = true)
                }
            }
        }
    }

    fun retrySavings() = observeSavings()

    fun updateDisplayTime() = mutableState.update { it.copy(now = clock.instant()) }

    fun selectBank(bank: Bank) {
        if (bank == mutableState.value.bank || bank !in rates.banks) return
        bankSession++
        rateObservation?.cancel()
        historyObservation?.cancel()
        refreshJob?.cancel()
        savedState[BANK_KEY] = bank.name
        mutableState.update {
            it.copy(
                bank = bank,
                rate = null,
                history = emptyList(),
                loadingRate = true,
                rateReadFailed = false,
                loadingHistory = true,
                historyReadFailed = false,
                refreshingRate = false,
                refreshFailed = false,
            )
        }
        observeBankData()
    }

    fun refreshRate() {
        val state = mutableState.value
        if (state.refreshingRate) return
        val bank = state.bank
        val session = bankSession
        mutableState.update { it.copy(refreshingRate = true, refreshFailed = false) }
        refreshJob = viewModelScope.launch {
            try {
                when (rates[bank].refreshUsdEgpRate()) {
                    is RefreshOutcome.Failure -> if (session == bankSession) {
                        mutableState.update { it.copy(refreshFailed = true) }
                    }
                    else -> Unit
                }
            } finally {
                if (session == bankSession) mutableState.update { it.copy(refreshingRate = false) }
            }
        }
    }

    private fun observeSettings() {
        settingsObservation?.cancel()
        settingsObservation = viewModelScope.launch {
            try {
                settings.observeSettings().collect { appSettings ->
                    mutableState.update { it.copy(intervalHours = appSettings.updateInterval.hours) }
                }
            } catch (_: StorageReadException) {
                // The default one-hour gap threshold remains safe when preferences are unavailable.
            }
        }
    }

    private fun observeSavings() {
        savingsObservation?.cancel()
        savingsObservation = viewModelScope.launch {
            try {
                savings.observeSavings().collect { balance ->
                    mutableState.update { state ->
                        state.copy(
                            savedBalance = balance,
                            usdInput = if (inputsInitialized) state.usdInput else balance.usd.editableAmount(),
                            egpInput = if (inputsInitialized) state.egpInput else balance.egp.editableAmount(),
                            loadingSavings = false,
                            savingsReadFailed = false,
                        )
                    }
                    inputsInitialized = true
                }
            } catch (_: StorageReadException) {
                mutableState.update { it.copy(loadingSavings = false, savingsReadFailed = true) }
            }
        }
    }

    private fun observeBankData() {
        observeRate()
        observeHistory()
    }

    private fun observeRate() {
        val bank = mutableState.value.bank
        val session = bankSession
        rateObservation = viewModelScope.launch {
            try {
                rates[bank].observeLatestUsdEgpRate().collect { rate ->
                    if (session == bankSession) mutableState.update {
                        it.copy(rate = rate, loadingRate = false, rateReadFailed = false)
                    }
                }
            } catch (_: StorageReadException) {
                if (session == bankSession) mutableState.update {
                    it.copy(loadingRate = false, rateReadFailed = true)
                }
            }
        }
    }

    private fun observeHistory() {
        val bank = mutableState.value.bank
        val session = bankSession
        historyObservation = viewModelScope.launch {
            try {
                rates[bank].observeUsdEgpHistory().collect { history ->
                    if (session == bankSession) mutableState.update {
                        it.copy(history = history, loadingHistory = false, historyReadFailed = false)
                    }
                }
            } catch (_: StorageReadException) {
                if (session == bankSession) mutableState.update {
                    it.copy(loadingHistory = false, historyReadFailed = true)
                }
            }
        }
    }

    private fun java.math.BigDecimal.editableAmount(): String =
        stripTrailingZeros().toPlainString()

    private companion object {
        const val BANK_KEY = "wallet_bank"
    }
}
