package com.example.currencyraise.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.currencyraise.domain.model.SettingsWriteResult
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.model.UpdateInterval
import com.example.currencyraise.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null
    private var permissionPrepared = false

    init { retryRead() }

    fun retryRead() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, message = null) }
        observation = viewModelScope.launch {
            try {
                repository.observeSettings().collect { settings ->
                    mutableState.update { it.copy(settings = settings, loading = false, readFailed = false) }
                }
            } catch (_: StorageReadException) {
                mutableState.update { it.copy(loading = false, readFailed = true) }
            }
        }
    }

    fun setInterval(interval: UpdateInterval) {
        launchWrite { repository.setUpdateInterval(interval) }
    }
    fun setAutomatic(enabled: Boolean) {
        launchWrite { repository.setAutomaticChecksEnabled(enabled) }
    }
    fun setNotifications(enabled: Boolean) {
        launchWrite { repository.setNotificationsEnabled(enabled) }
    }

    private fun launchWrite(block: suspend () -> SettingsWriteResult) {
        if (!mutableState.value.editable) return
        // Reserve before launching, so rapid taps cannot queue conflicting edits.
        mutableState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch { finishWrite(block) }
    }

    /** Explicit UI action only. Persist before asking, so denial is remembered across restarts. */
    suspend fun preparePermissionRequest(): Boolean {
        val state = mutableState.value
        if (!state.editable || state.settings?.notificationsEnabled != true ||
            state.settings.notificationPermissionAsked || permissionPrepared) return false
        permissionPrepared = true
        mutableState.update { it.copy(saving = true, message = null) }
        return try {
            // Re-read before requesting: an earlier cancelled UI action may already have saved the marker.
            val latest = repository.observeSettings().first()
            mutableState.update { it.copy(settings = latest) }
            if (latest.notificationPermissionAsked || !latest.notificationsEnabled) return false
            finishWrite { repository.markNotificationPermissionAsked() }
        } catch (_: StorageReadException) {
            mutableState.update { it.copy(readFailed = true) }
            false
        } finally {
            permissionPrepared = false
            mutableState.update { it.copy(saving = false) }
        }
    }

    private suspend fun finishWrite(block: suspend () -> SettingsWriteResult): Boolean {
        try {
            val saved = block() == SettingsWriteResult.SAVED
            mutableState.update {
                it.copy(message = if (saved) SettingsMessage.SAVED else SettingsMessage.WRITE_FAILED)
            }
            return saved
        } finally {
            mutableState.update { it.copy(saving = false) }
        }
    }
}
