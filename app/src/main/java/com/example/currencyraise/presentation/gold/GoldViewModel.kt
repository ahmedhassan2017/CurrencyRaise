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
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restoredKarat = GoldKarat.entries.firstOrNull {
        it.name == savedState.get<String>("gold_karat")
    } ?: GoldKarat.K21
    private val mutableState = MutableStateFlow(GoldUiState(karat = restoredKarat, now = clock.instant()))
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null
    private var refreshJob: Job? = null
    private var session = 0
    private var initialCheckPending = true
    private var active = false

    init {
        observeRate()
    }

    fun selectKarat(karat: GoldKarat) {
        if (karat == mutableState.value.karat) return
        session++
        refreshJob?.cancel()
        observation?.cancel()
        savedState["gold_karat"] = karat.name
        initialCheckPending = true
        mutableState.value = GoldUiState(karat = karat, now = clock.instant())
        observeRate()
    }

    fun refresh() = startRefresh(Duration.ZERO)

    fun setActive(value: Boolean) {
        active = value
        if (value) maybeInitialRefresh()
    }

    /** Called only while the Gold tab is visible; the repository enforces the five-minute floor. */
    fun refreshIfDue() {
        mutableState.update { it.copy(now = clock.instant()) }
        if (mutableState.value.freshness != GoldFreshness.RECENT_CHECK) {
            startRefresh(GOLD_CHECK_INTERVAL)
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

    private fun maybeInitialRefresh() {
        val state = mutableState.value
        if (!active || !initialCheckPending || state.loadingCache) return
        initialCheckPending = false
        if (!state.rateReadFailed && state.freshness != GoldFreshness.RECENT_CHECK) {
            startRefresh(GOLD_CHECK_INTERVAL)
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
