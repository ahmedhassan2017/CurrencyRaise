package com.example.currencyraise.presentation.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.NotificationAccess
import com.example.currencyraise.domain.model.NotificationAccessStatus
import com.example.currencyraise.domain.model.UpdateInterval
import com.example.currencyraise.notification.SystemNotificationAccess
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemAccess = remember(context) { SystemNotificationAccess(context) }
    var access by remember { mutableStateOf(systemAccess.read()) }
    var settingsOpenFailed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = systemAccess.read()
    }
    // Includes returning from Android settings; OS state is never inferred from the app toggle.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = systemAccess.read() }
    SettingsScreen(
        state, access, onBack, viewModel::setInterval, viewModel::setAutomatic,
        viewModel::setNotifications, viewModel::retryRead,
        onPermissionAction = {
            access = systemAccess.read()
            when (notificationAction(viewModel.uiState.value.settings, access)) {
                PermissionAction.REQUEST -> scope.launch {
                    if (Build.VERSION.SDK_INT >= 33 && viewModel.preparePermissionRequest()) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                PermissionAction.OPEN_SETTINGS -> {
                    try {
                        context.startActivity(systemAccess.settingsIntent(access.status))
                    } catch (_: ActivityNotFoundException) {
                        try {
                            context.startActivity(systemAccess.appDetailsIntent())
                        } catch (_: ActivityNotFoundException) {
                            settingsOpenFailed = true
                        }
                    }
                }
                PermissionAction.NONE -> Unit
            }
        },
    )
    if (settingsOpenFailed) {
        AlertDialog(
            onDismissRequest = { settingsOpenFailed = false },
            title = { Text(stringResource(R.string.notification_settings_title)) },
            text = { Text(stringResource(R.string.notification_settings_unavailable)) },
            confirmButton = {
                TextButton(onClick = { settingsOpenFailed = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    access: NotificationAccess,
    onBack: () -> Unit,
    onInterval: (UpdateInterval) -> Unit,
    onAutomatic: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onPermissionAction: () -> Unit,
) {
    var intervalDialog by rememberSaveable { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        Column(
            Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back_home)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodyMedium)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.readFailed) {
                Text(stringResource(R.string.settings_read_failed), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Button(onClick = onRetry, enabled = !state.loading) { Text(stringResource(R.string.try_again)) }
                Text(stringResource(R.string.storage_recovery), style = MaterialTheme.typography.bodySmall)
            }
            state.settings?.let { settings ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.background_inactive), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.preferences_preview), style = MaterialTheme.typography.bodySmall)
                    }
                }
                SectionTitle(stringResource(R.string.background_section))
                ToggleSetting(
                    stringResource(R.string.automatic_checks), stringResource(R.string.automatic_preference_help),
                    settings.automaticChecksEnabled, state.editable, onAutomatic,
                )
                OutlinedButton(
                    onClick = { intervalDialog = true },
                    enabled = state.editable && settings.automaticChecksEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(pluralStringResource(R.plurals.choose_interval, settings.updateInterval.hours, settings.updateInterval.hours))
                }
                Text(stringResource(R.string.interval_approximate), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                SectionTitle(stringResource(R.string.notifications_section))
                ToggleSetting(
                    stringResource(R.string.rate_alerts), stringResource(R.string.rate_alerts_help),
                    settings.notificationsEnabled, state.editable, onNotifications,
                )
                Text(stringResource(when (access.status) {
                    NotificationAccessStatus.ALLOWED -> R.string.android_notifications_allowed
                    NotificationAccessStatus.PERMISSION_NEEDED -> R.string.android_permission_needed
                    NotificationAccessStatus.APP_BLOCKED -> R.string.android_notifications_blocked
                    NotificationAccessStatus.CHANNEL_BLOCKED -> R.string.android_channel_blocked
                }), style = MaterialTheme.typography.bodyMedium)
                val action = notificationAction(settings, access)
                if (action != PermissionAction.NONE) {
                    Text(stringResource(R.string.permission_context), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onPermissionAction, enabled = state.editable) {
                        Text(stringResource(if (action == PermissionAction.REQUEST) R.string.allow_notifications
                            else R.string.open_notification_settings))
                    }
                }
                if (!settings.automaticChecksEnabled && settings.notificationsEnabled) {
                    Text(stringResource(R.string.alerts_paused), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                SectionTitle(stringResource(R.string.source_section))
                Text(stringResource(R.string.source_cash, stringResource(R.string.bank_name)))
                Text(stringResource(R.string.rate_unit), style = MaterialTheme.typography.bodySmall)
            }
            if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(when {
                    state.saving -> R.string.saving_preferences
                    state.message == SettingsMessage.WRITE_FAILED -> R.string.settings_write_failed
                    state.message == SettingsMessage.SAVED -> R.string.preferences_saved
                    else -> R.string.preferences_auto_save
                }),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.message == SettingsMessage.WRITE_FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (intervalDialog) {
        AlertDialog(
            onDismissRequest = { intervalDialog = false },
            title = { Text(stringResource(R.string.interval_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UpdateInterval.entries.forEach { interval ->
                        TextButton(
                            onClick = { onInterval(interval); intervalDialog = false },
                            enabled = state.editable && state.settings?.automaticChecksEnabled == true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(pluralStringResource(R.plurals.interval_value, interval.hours, interval.hours))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { intervalDialog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ToggleSetting(label: String, explanation: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
