package com.example.currencyraise.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.model.RefreshError
import com.example.currencyraise.domain.model.RefreshOutcome
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.ExchangeRateRepository
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
    private val rates: ExchangeRateRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState(now = clock.instant()))
    val uiState = mutableState.asStateFlow()
    private var rateObservation: Job? = null
    private var settingsObservation: Job? = null
    private var initialCheckPending = true
    private var settingsLoaded = false

    init {
        observeRates()
        observeSettings()
    }

    private fun observeRates() {
        rateObservation?.cancel()
        rateObservation = viewModelScope.launch {
            try {
                rates.observeLatestUsdEgpRate().collect { rate ->
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
        if (state.settingsReadFailed) observeSettings()
        viewModelScope.launch {
            try {
                when (val result = rates.refreshUsdEgpRate()) {
                    is RefreshOutcome.Cached -> Unit
                    is RefreshOutcome.Success ->
                        mutableState.update { it.copy(lastChange = result.change) }
                    is RefreshOutcome.Failure ->
                        mutableState.update { it.copy(refreshError = result.error.toHomeError()) }
                    RefreshOutcome.AlreadyRefreshing ->
                        mutableState.update { it.copy(alreadyRefreshing = true) }
                }
            } finally {
                mutableState.update { it.copy(refreshing = false, now = clock.instant()) }
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
