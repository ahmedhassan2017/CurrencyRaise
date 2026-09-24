package com.example.currencyraise.presentation.gold

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.currencyraise.domain.model.GoldKarat
import com.example.currencyraise.domain.model.GoldRefreshOutcome
import com.example.currencyraise.domain.model.RateFetchError
import com.example.currencyraise.domain.model.RefreshError
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.GoldRateRepository
import com.example.currencyraise.domain.repository.SettingsRepository
import com.example.currencyraise.presentation.home.HomeError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GoldViewModel @Inject constructor(
    private val repository: GoldRateRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restoredKarat = GoldKarat.entries.firstOrNull {
        it.name == savedState.get<String>("gold_karat")
    } ?: GoldKarat.K21
    private val mutableState = MutableStateFlow(GoldUiState(karat = restoredKarat, now = clock.instant()))
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null
    private var historyObservation: Job? = null
    private var settingsObservation: Job? = null
    private var refreshJob: Job? = null
    private var session = 0
    private var initialCheckPending = true
    private var active = false
    private var settingsLoaded = false

    init {
        observeRate()
        observeHistory()
        observeSettings()
    }

    fun selectKarat(karat: GoldKarat) {
        if (karat == mutableState.value.karat) return
        session++
        refreshJob?.cancel()
        observation?.cancel()
        historyObservation?.cancel()
        savedState["gold_karat"] = karat.name
        initialCheckPending = true
        val updateInterval = mutableState.value.updateInterval
        mutableState.value = GoldUiState(
            karat = karat,
            now = clock.instant(),
            updateInterval = updateInterval,
        )
        observeRate()
        observeHistory()
    }

    fun refresh() = startRefresh(Duration.ZERO)

    fun retryHistory() = observeHistory()

    fun setActive(value: Boolean) {
        active = value
        if (value) maybeInitialRefresh()
    }

    /** Called only while the Gold tab is visible; the saved shared interval limits provider calls. */
    fun refreshIfDue() {
        mutableState.update { it.copy(now = clock.instant()) }
        if (mutableState.value.freshness != GoldFreshness.RECENT_CHECK) {
            startRefresh(mutableState.value.updateInterval.duration)
        }
    }

    private fun observeRate() {
        observation?.cancel()
        val karat = mutableState.value.karat
        val expectedSession = session
        observation = viewModelScope.launch {
            try {
                repository.observeLatest(karat).collect { rate ->
                    if (session != expectedSession) return@collect
                    mutableState.update {
                        it.copy(
                            rate = rate,
                            loadingCache = false,
                            rateReadFailed = false,
                            now = clock.instant(),
                            refreshError = if (rate != null && rate != it.rate) null else it.refreshError,
                            alreadyRefreshing = if (rate != it.rate) false else it.alreadyRefreshing,
                        )
                    }
                    maybeInitialRefresh()
                }
            } catch (_: StorageReadException) {
                if (session != expectedSession) return@launch
                mutableState.update { it.copy(loadingCache = false, rateReadFailed = true) }
                maybeInitialRefresh()
            }
        }
    }

    private fun observeHistory() {
        historyObservation?.cancel()
        val karat = mutableState.value.karat
        val expectedSession = session
        historyObservation = viewModelScope.launch {
            try {
                repository.observeHistory(karat).collect { history ->
                    if (session != expectedSession) return@collect
                    mutableState.update {
                        it.copy(
                            history = history,
                            loadingHistory = false,
                            historyReadFailed = false,
                        )
                    }
                }
            } catch (_: StorageReadException) {
                if (session != expectedSession) return@launch
                mutableState.update {
                    it.copy(loadingHistory = false, historyReadFailed = true)
                }
            }
        }
    }

    private fun maybeInitialRefresh() {
        val state = mutableState.value
        if (!active || !initialCheckPending || state.loadingCache || !settingsLoaded) return
        initialCheckPending = false
        if (!state.rateReadFailed && state.freshness != GoldFreshness.RECENT_CHECK) {
            startRefresh(state.updateInterval.duration)
        }
    }

    private fun observeSettings() {
        settingsObservation?.cancel()
        settingsObservation = viewModelScope.launch {
            try {
                settings.observeSettings().collect { appSettings ->
                    settingsLoaded = true
                    mutableState.update {
                        it.copy(updateInterval = appSettings.updateInterval, now = clock.instant())
                    }
                    maybeInitialRefresh()
                }
            } catch (_: StorageReadException) {
                // Keep the safe one-hour default when preferences cannot be read.
                settingsLoaded = true
                maybeInitialRefresh()
            }
        }
    }

    private fun startRefresh(minimumAge: Duration) {
        val state = mutableState.value
        if (state.refreshing || state.loadingCache) return
        initialCheckPending = false
        mutableState.update {
            it.copy(refreshing = true, refreshError = null, lastChange = null, alreadyRefreshing = false)
        }
        if (state.rateReadFailed) observeRate()
        val karat = state.karat
        val expectedSession = session
        refreshJob = viewModelScope.launch {
            try {
                val result = repository.refresh(karat, minimumAge)
                if (session != expectedSession) return@launch
                when (result) {
                    is GoldRefreshOutcome.Cached -> Unit
                    is GoldRefreshOutcome.Success -> mutableState.update { it.copy(lastChange = result.change) }
                    is GoldRefreshOutcome.Failure -> mutableState.update {
                        it.copy(refreshError = result.error.toHomeError())
                    }
                    GoldRefreshOutcome.AlreadyRefreshing -> mutableState.update {
                        it.copy(alreadyRefreshing = true)
                    }
                }
            } finally {
                mutableState.update {
                    if (session == expectedSession) it.copy(refreshing = false, now = clock.instant()) else it
                }
            }
        }
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
        is RateFetchError.Http -> if (error.statusCode == 429) HomeError.RATE_LIMITED else HomeError.PROVIDER
    }
}
