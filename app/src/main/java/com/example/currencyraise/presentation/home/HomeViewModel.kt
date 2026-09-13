package com.example.currencyraise.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.model.RefreshError
import com.example.currencyraise.domain.model.RefreshOutcome
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.BankRateRepositories
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val banks: BankRateRepositories,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restoredBank = Bank.entries.firstOrNull {
        it.name == savedState.get<String>("bank") && it in banks.banks
    } ?: banks.banks.first()
    private val mutableState = MutableStateFlow(HomeUiState(
        bank = restoredBank, banks = banks.banks, now = clock.instant(),
    ))
    val uiState = mutableState.asStateFlow()
    private var rateObservation: Job? = null
    private var settingsObservation: Job? = null
    private var historyObservation: Job? = null
    private var refreshJob: Job? = null
    private var rateSession = 0
    private var initialCheckPending = true
    private var settingsLoaded = false

    init {
        observeRates()
        observeHistory()
        observeSettings()
    }

    fun selectBank(bank: Bank) {
        if (bank == mutableState.value.bank || bank !in banks.banks) return
        rateSession++
        refreshJob?.cancel()
        rateObservation?.cancel()
        historyObservation?.cancel()
        savedState["bank"] = bank.name
        initialCheckPending = true
        mutableState.update { old -> HomeUiState(
            bank = bank, banks = banks.banks, settings = old.settings,
            settingsReadFailed = old.settingsReadFailed, now = clock.instant(),
        ) }
        observeRates()
        observeHistory()
    }

    fun retryHistory() = observeHistory()

    private fun observeHistory() {
        historyObservation?.cancel()
        val bank = mutableState.value.bank
        val session = rateSession
        historyObservation = viewModelScope.launch {
            try {
                banks[bank].observeUsdEgpHistory().collect { history ->
                    if (rateSession == session) mutableState.update {
                        it.copy(history = history, loadingHistory = false, historyReadFailed = false)
                    }
                }
            } catch (_: StorageReadException) {
                if (rateSession == session) mutableState.update {
                    it.copy(loadingHistory = false, historyReadFailed = true)
                }
            }
        }
    }

    private fun observeRates() {
        rateObservation?.cancel()
        val bank = mutableState.value.bank
        val session = rateSession
        rateObservation = viewModelScope.launch {
            try {
                banks[bank].observeLatestUsdEgpRate().collect { rate ->
                    if (rateSession != session) return@collect
                    mutableState.update {
                        it.copy(
                            rate = rate, loadingCache = false, rateReadFailed = false,
                            now = clock.instant(),
                            refreshError = if (rate != null && rate != it.rate) null else it.refreshError,
                            alreadyRefreshing = if (rate != it.rate) false else it.alreadyRefreshing,
                        )
                    }
                    maybeInitialRefresh()
                }
            } catch (_: StorageReadException) {
                if (rateSession != session) return@launch
                mutableState.update { it.copy(loadingCache = false, rateReadFailed = true) }
                maybeInitialRefresh()
            }
        }
    }

    private fun observeSettings() {
        settingsObservation?.cancel()
        settingsObservation = viewModelScope.launch {
            try {
                settings.observeSettings().collect { value ->
                    settingsLoaded = true
                    mutableState.update { it.copy(settings = value, settingsReadFailed = false) }
                    maybeInitialRefresh()
                }
            } catch (_: StorageReadException) {
                settingsLoaded = true
                mutableState.update { it.copy(settingsReadFailed = true) }
                maybeInitialRefresh()
            }
        }
    }

    private fun maybeInitialRefresh() {
        val state = mutableState.value
        if (!initialCheckPending || state.loadingCache || !settingsLoaded) return
        initialCheckPending = false
        // An unreadable cache needs explicit retry. A future timestamp is unverified.
        if (!state.rateReadFailed && state.freshness != Freshness.RECENT_CHECK) refresh()
    }

    /** Called on the main thread by UI events. Cache observations remain the source of quote state. */
    fun refresh() {
        val state = mutableState.value
        if (state.refreshing || state.loadingCache) return
        initialCheckPending = false
        mutableState.update {
            it.copy(refreshing = true, refreshError = null, lastChange = null, alreadyRefreshing = false)
        }
        if (state.rateReadFailed) observeRates()
        if (state.historyReadFailed) observeHistory()
        if (state.settingsReadFailed) observeSettings()
        val bank = state.bank
        val session = rateSession
        refreshJob = viewModelScope.launch {
            try {
                val result = banks[bank].refreshUsdEgpRate()
                if (rateSession != session) return@launch
                when (result) {
                    is RefreshOutcome.Cached -> Unit
                    is RefreshOutcome.Success ->
                        mutableState.update { it.copy(lastChange = result.change) }
                    is RefreshOutcome.Failure ->
                        mutableState.update { it.copy(refreshError = result.error.toHomeError()) }
                    RefreshOutcome.AlreadyRefreshing ->
                        mutableState.update { it.copy(alreadyRefreshing = true) }
                }
            } finally {
                mutableState.update {
                    if (rateSession == session) it.copy(refreshing = false, now = clock.instant()) else it
                }
            }
        }
    }

    /** Foreground display clock only; this never schedules polling or performs HTTP. */
    fun updateDisplayTime() {
        mutableState.update { it.copy(now = clock.instant()) }
    }
}

private fun RefreshError.toHomeError(): HomeError = when (this) {
    is RefreshError.Deferred -> HomeError.DEFERRED
    RefreshError.StorageRead -> HomeError.STORAGE_READ
    RefreshError.StorageWrite -> HomeError.STORAGE_WRITE
    is RefreshError.Fetch -> when (error) {
        RateFetchError.Network -> HomeError.NETWORK
        RateFetchError.Timeout -> HomeError.TIMEOUT
        RateFetchError.InvalidResponse -> HomeError.PROVIDER
        is RateFetchError.Http ->
            if (error.statusCode == 429) HomeError.RATE_LIMITED else HomeError.PROVIDER
    }
}
