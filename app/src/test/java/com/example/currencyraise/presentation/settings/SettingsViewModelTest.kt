package com.example.currencyraise.presentation.settings

import androidx.lifecycle.ViewModelStore
import com.example.currencyraise.domain.model.*
import com.example.currencyraise.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val repository = FakeSettings()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owner.clear(); Dispatchers.resetMain() }
    private fun create() = SettingsViewModel(repository).also { owner.put("settings", it) }

    @Test fun loadsDefaultsAndPersistsIndependentChoices() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        assertEquals(AppSettings(), vm.uiState.value.settings)
        vm.setInterval(UpdateInterval.SIX_HOURS)
        runCurrent()
        vm.setAutomatic(false)
        runCurrent()
        vm.setNotifications(false)
        runCurrent()
        vm.setAppearance(AppearanceMode.DARK)
        runCurrent()
        assertEquals(
            AppSettings(UpdateInterval.SIX_HOURS, false, false, appearanceMode = AppearanceMode.DARK),
            vm.uiState.value.settings,
        )
        assertEquals(SettingsMessage.SAVED, vm.uiState.value.message)
    }

    @Test fun failedWriteRetainsValueAndCanRetry() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        repository.failWrite = true
        vm.setAutomatic(false)
        runCurrent()
        assertTrue(vm.uiState.value.settings!!.automaticChecksEnabled)
        assertEquals(SettingsMessage.WRITE_FAILED, vm.uiState.value.message)
        repository.failWrite = false
        vm.setAutomatic(false)
        runCurrent()
        assertFalse(vm.uiState.value.settings!!.automaticChecksEnabled)
    }

    @Test fun readFailureDisablesEditingAndRetryRestoresObservation() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        repository.saved.value = Result.failure(StorageReadException(IOException()))
        runCurrent()
        assertTrue(vm.uiState.value.readFailed)
        assertFalse(vm.uiState.value.editable)
        assertEquals(AppSettings(), vm.uiState.value.settings)
        vm.setAutomatic(false)
        runCurrent()
        assertEquals(0, repository.writes)
        repository.saved.value = Result.success(AppSettings(updateInterval = UpdateInterval.DAILY))
        vm.retryRead()
        runCurrent()
        assertTrue(vm.uiState.value.editable)
        assertEquals(UpdateInterval.DAILY, vm.uiState.value.settings?.updateInterval)
    }

    @Test fun rapidEditsCannotQueueConflictingWrites() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        repository.gate = CompletableDeferred()
        vm.setAutomatic(false)
        vm.setAutomatic(true)
        runCurrent()
        assertTrue(vm.uiState.value.saving)
        assertEquals(1, repository.writes)
        repository.gate!!.complete(Unit)
        runCurrent()
        assertFalse(vm.uiState.value.settings!!.automaticChecksEnabled)
        assertFalse(vm.uiState.value.saving)
    }

    @Test fun permissionPreparationPersistsBeforeAllowingPromptAndSurvivesRecreation() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        assertTrue(vm.preparePermissionRequest())
        runCurrent()
        assertTrue(repository.saved.value.getOrThrow().notificationPermissionAsked)
        assertFalse(vm.preparePermissionRequest())
        owner.clear()
        val restored = create()
        runCurrent()
        assertFalse(restored.preparePermissionRequest())
        assertEquals(1, repository.writes)
    }

    @Test fun storageFailurePreventsPermissionPromptAndRetryWorks() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        repository.failWrite = true
        assertFalse(vm.preparePermissionRequest())
        assertFalse(repository.saved.value.getOrThrow().notificationPermissionAsked)
        repository.failWrite = false
        assertTrue(vm.preparePermissionRequest())
    }

    @Test fun alertsOffCannotRequestPermission() = runTest(dispatcher) {
        repository.saved.value = Result.success(AppSettings(notificationsEnabled = false))
        val vm = create()
        runCurrent()
        assertFalse(vm.preparePermissionRequest())
        assertEquals(0, repository.writes)
    }

    @Test fun cancellingPermissionPreparationDoesNotLeaveControlsLocked() = runTest(dispatcher) {
        val vm = create()
        runCurrent()
        repository.gate = CompletableDeferred()
        val pending = async { vm.preparePermissionRequest() }
        runCurrent()
        pending.cancel()
        runCurrent()
        assertFalse(vm.uiState.value.saving)
        repository.gate = null
        assertTrue(vm.preparePermissionRequest())
    }

    private class FakeSettings : SettingsRepository {
        val saved = MutableStateFlow(Result.success(AppSettings()))
        var failWrite = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeSettings() = saved.map { it.getOrThrow() }
        private suspend fun write(change: (AppSettings) -> AppSettings): SettingsWriteResult {
            writes++
            gate?.await()
            if (failWrite) return SettingsWriteResult.STORAGE_FAILURE
            saved.value = Result.success(change(saved.value.getOrThrow()))
            return SettingsWriteResult.SAVED
        }
        override suspend fun setUpdateInterval(interval: UpdateInterval) = write { it.copy(updateInterval = interval) }
        override suspend fun setAutomaticChecksEnabled(enabled: Boolean) = write { it.copy(automaticChecksEnabled = enabled) }
        override suspend fun setNotificationsEnabled(enabled: Boolean) = write { it.copy(notificationsEnabled = enabled) }
        override suspend fun setAppearanceMode(mode: AppearanceMode) = write { it.copy(appearanceMode = mode) }
        override suspend fun markNotificationPermissionAsked() = write { it.copy(notificationPermissionAsked = true) }
    }
}
