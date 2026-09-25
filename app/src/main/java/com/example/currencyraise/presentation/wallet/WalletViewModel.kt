package com.example.currencyraise.presentation.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RefreshOutcome
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.domain.model.SavingsWriteResult
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.BankRateRepositories
import com.example.currencyraise.domain.repository.GoldRateRepository
import com.example.currencyraise.domain.repository.SavingsRepository
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
    private val goldRates: GoldRateRepository,
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
    private var goldRateObservation: Job? = null
    private var goldHistoryObservation: Job? = null
    private var refreshJob: Job? = null
    private var bankSession = 0
    private var goldSession = 0
    private var observedGoldKarat: GoldKarat? = null

    init {
        observeSavings()
        observeBankData()
    }

    fun updateUsdInput(value: String) = updateInput(usd = normalizeCurrencyInput(value))

    fun updateEgpInput(value: String) = updateInput(egp = normalizeCurrencyInput(value))

    fun updateGoldInput(value: String) = updateInput(gold = normalizeCurrencyInput(value, 3))

    fun selectGoldKarat(karat: GoldKarat) {
        if (karat == mutableState.value.goldKarat) return
        mutableState.update {
            it.copy(goldKarat = karat, saveFailed = false, saveConfirmed = false)
        }
        if (mutableState.value.savedBalance.goldGrams.signum() == 0) observeGoldData(karat)
    }

    private fun updateInput(usd: String? = null, egp: String? = null, gold: String? = null) {
        mutableState.update {
            it.copy(
                usdInput = usd ?: it.usdInput,
                egpInput = egp ?: it.egpInput,
                goldInput = gold ?: it.goldInput,
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
                SavingsWriteResult.SAVED -> {
                    mutableState.update {
                        it.copy(saving = false, saveConfirmed = true, savedBalance = balance)
                    }
                    observeGoldData(balance.goldKarat)
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
        val karat = trackedGoldKarat(state)
        val session = bankSession
        val expectedGoldSession = goldSession
        mutableState.update { it.copy(refreshingRate = true, refreshFailed = false) }
        refreshJob = viewModelScope.launch {
            try {
                val bankFailed = when (rates[bank].refreshUsdEgpRate()) {
                    is RefreshOutcome.Failure -> true
                    else -> false
                }
                val goldFailed = when (goldRates.refresh(karat)) {
                    is GoldRefreshOutcome.Failure -> true
                    else -> false
                }
                if (session == bankSession && expectedGoldSession == goldSession &&
                    (bankFailed || goldFailed)
                ) {
                    mutableState.update { it.copy(refreshFailed = true) }
                }
            } finally {
                if (session == bankSession) mutableState.update { it.copy(refreshingRate = false) }
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
                            goldInput = if (inputsInitialized) state.goldInput else balance.goldGrams.editableAmount(),
                            goldKarat = if (inputsInitialized) state.goldKarat else balance.goldKarat,
                            loadingSavings = false,
                            savingsReadFailed = false,
                        )
                    }
                    observeGoldData(trackedGoldKarat(mutableState.value))
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
        observeGoldData(trackedGoldKarat(mutableState.value))
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

    private fun observeGoldData(karat: GoldKarat) {
        if (observedGoldKarat == karat && goldRateObservation?.isActive == true &&
            goldHistoryObservation?.isActive == true
        ) return
        observedGoldKarat = karat
        goldSession++
        val session = goldSession
        goldRateObservation?.cancel()
        goldHistoryObservation?.cancel()
        mutableState.update {
            it.copy(
                goldRate = null,
                goldHistory = emptyList(),
                loadingGoldRate = true,
                goldRateReadFailed = false,
                loadingGoldHistory = true,
                goldHistoryReadFailed = false,
            )
        }
        goldRateObservation = viewModelScope.launch {
            try {
                goldRates.observeLatest(karat).collect { rate ->
                    if (session == goldSession) mutableState.update {
                        it.copy(goldRate = rate, loadingGoldRate = false, goldRateReadFailed = false)
                    }
                }
            } catch (_: StorageReadException) {
                if (session == goldSession) mutableState.update {
                    it.copy(loadingGoldRate = false, goldRateReadFailed = true)
                }
            }
        }
        goldHistoryObservation = viewModelScope.launch {
            try {
                goldRates.observeHistory(karat).collect { history ->
                    if (session == goldSession) mutableState.update {
                        it.copy(
                            goldHistory = history,
                            loadingGoldHistory = false,
                            goldHistoryReadFailed = false,
                        )
                    }
                }
            } catch (_: StorageReadException) {
                if (session == goldSession) mutableState.update {
                    it.copy(loadingGoldHistory = false, goldHistoryReadFailed = true)
                }
            }
        }
    }

    private fun trackedGoldKarat(state: WalletUiState): GoldKarat =
        if (state.savedBalance.goldGrams.signum() > 0) state.savedBalance.goldKarat else state.goldKarat

    private fun java.math.BigDecimal.editableAmount(): String =
        stripTrailingZeros().toPlainString()

    private companion object {
        const val BANK_KEY = "wallet_bank"
    }
}
