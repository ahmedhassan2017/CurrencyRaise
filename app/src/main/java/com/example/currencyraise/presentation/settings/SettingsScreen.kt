package com.example.currencyraise.presentation.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.NotificationAccess
import com.example.currencyraise.domain.model.NotificationAccessStatus
import com.example.currencyraise.domain.model.QuoteKind
import com.example.currencyraise.domain.model.UpdateInterval
import com.example.currencyraise.notification.RateNotificationPublisher
import com.example.currencyraise.notification.SystemNotificationAccess
import com.example.currencyraise.presentation.background.BackgroundStatusRoute
import com.example.currencyraise.presentation.background.BackgroundStatusSection
import com.example.currencyraise.presentation.components.CurrencyPanel
import com.example.currencyraise.presentation.components.CurrencyRaiseHeader
import com.example.currencyraise.presentation.components.SectionLabel
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemAccess = remember(context) { SystemNotificationAccess(context) }
    var access by remember { mutableStateOf(systemAccess.read()) }
    var settingsOpenFailed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val testNotificationPublisher = remember(context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            RateNotificationPublisher(context.applicationContext)
        } else null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = systemAccess.read()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = systemAccess.read() }
    SettingsScreen(
        state = state,
        access = access,
        onBack = onBack,
        onInterval = viewModel::setInterval,
        onAutomatic = viewModel::setAutomatic,
        onNotifications = viewModel::setNotifications,
        onRetry = viewModel::retryRead,
        backgroundStatus = { BackgroundStatusRoute(showTitle = false) },
        showTestNotification = testNotificationPublisher != null,
        onSendTestNotification = {
            testNotificationPublisher?.publishTest(debugNotificationRate()) == true
        },
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
                TextButton(shape = MaterialTheme.shapes.small, onClick = { settingsOpenFailed = false }) { Text(stringResource(R.string.close)) }
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
    backgroundStatus: @Composable () -> Unit = { BackgroundStatusSection(showTitle = false) },
    showTestNotification: Boolean = false,
    onSendTestNotification: () -> Boolean = { false },
) {
    var intervalDialog by rememberSaveable { mutableStateOf(false) }
    var testNotificationSent by rememberSaveable { mutableStateOf<Boolean?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .consumeWindowInsets(insets)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CurrencyRaiseHeader(
                actionLabel = stringResource(R.string.back_home),
                onAction = onBack,
            )

            Column(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            if (state.readFailed) {
                CurrencyPanel(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        stringResource(R.string.settings_read_failed),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Button(shape = MaterialTheme.shapes.small, onClick = onRetry, enabled = !state.loading) {
                        Text(stringResource(R.string.try_again))
                    }
                    Text(
                        stringResource(R.string.storage_recovery),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            state.settings?.let { settings ->
                SectionLabel(stringResource(R.string.background_section))
                CurrencyPanel(containerColor = MaterialTheme.colorScheme.surface) {
                    backgroundStatus()
                }

                CurrencyPanel {
                    ToggleSetting(
                        label = stringResource(R.string.automatic_checks),
                        explanation = stringResource(R.string.automatic_preference_help),
                        checked = settings.automaticChecksEnabled,
                        enabled = state.editable,
                        onChange = onAutomatic,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OutlinedButton(
                        shape = MaterialTheme.shapes.small,
                        onClick = { intervalDialog = true },
                        enabled = state.editable && settings.automaticChecksEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.choose_interval,
                                settings.updateInterval.hours,
                                settings.updateInterval.hours,
                            ),
                        )
                    }
                    Text(
                        stringResource(R.string.interval_approximate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionLabel(stringResource(R.string.notifications_section))
                CurrencyPanel {
                    ToggleSetting(
                        label = stringResource(R.string.rate_alerts),
                        explanation = stringResource(R.string.rate_alerts_help),
                        checked = settings.notificationsEnabled,
                        enabled = state.editable,
                        onChange = onNotifications,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AccessPill(access.status)
                    val action = notificationAction(settings, access)
                    if (action != PermissionAction.NONE) {
                        Text(
                            stringResource(R.string.permission_context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            shape = MaterialTheme.shapes.small,
                            onClick = onPermissionAction,
                            enabled = state.editable,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (action == PermissionAction.REQUEST) R.string.allow_notifications
                                    else R.string.open_notification_settings,
                                ),
                            )
                        }
                    }
                    if (!settings.automaticChecksEnabled && settings.notificationsEnabled) {
                        Text(
                            stringResource(R.string.alerts_paused),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (showTestNotification) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SectionLabel(stringResource(R.string.debug_notification_section))
                        Text(
                            stringResource(R.string.debug_notification_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (access.status != NotificationAccessStatus.ALLOWED) {
                            Text(
                                stringResource(R.string.debug_notification_allow_first),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        testNotificationSent?.let { sent ->
                            Text(
                                stringResource(
                                    if (sent) R.string.debug_notification_sent
                                    else R.string.debug_notification_failed,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (sent) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        OutlinedButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = { testNotificationSent = onSendTestNotification() },
                            enabled = state.editable && access.status == NotificationAccessStatus.ALLOWED,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(stringResource(R.string.send_test_notification))
                        }
                    }
                }

                SectionLabel(stringResource(R.string.source_section))
                CurrencyPanel {
                    Text(
                        stringResource(R.string.supported_sources),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.rate_unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.saving) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            SaveStatus(state)
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
                            shape = MaterialTheme.shapes.small,
                            onClick = {
                                onInterval(interval)
                                intervalDialog = false
                            },
                            enabled = state.editable && state.settings?.automaticChecksEnabled == true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.interval_value,
                                    interval.hours,
                                    interval.hours,
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(shape = MaterialTheme.shapes.small, onClick = { intervalDialog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    explanation: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun AccessPill(status: NotificationAccessStatus) {
    val allowed = status == NotificationAccessStatus.ALLOWED
    val container = if (allowed) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.primaryContainer
    val content = if (allowed) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = container,
        contentColor = content,
        shape = CircleShape,
        border = BorderStroke(1.dp, content.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(7.dp),
                color = if (allowed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ) {}
            Spacer(Modifier.width(9.dp))
            Text(
                stringResource(
                    when (status) {
                        NotificationAccessStatus.ALLOWED -> R.string.android_notifications_allowed
                        NotificationAccessStatus.PERMISSION_NEEDED -> R.string.android_permission_needed
                        NotificationAccessStatus.APP_BLOCKED -> R.string.android_notifications_blocked
                        NotificationAccessStatus.CHANNEL_BLOCKED -> R.string.android_channel_blocked
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SaveStatus(state: SettingsUiState) {
    val failed = state.message == SettingsMessage.WRITE_FAILED
    Text(
        stringResource(
            when {
                state.saving -> R.string.saving_preferences
                failed -> R.string.settings_write_failed
                state.message == SettingsMessage.SAVED -> R.string.preferences_saved
                else -> R.string.preferences_auto_save
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun debugNotificationRate() = ExchangeRate(
    baseCurrency = "USD",
    quoteCurrency = "EGP",
    buyRate = BigDecimal("51.27"),
    sellRate = BigDecimal("51.37"),
    sourceId = "debug_notification_test",
    sourceName = "Currency Raise test",
    sourceUrl = "https://www.banquemisr.com/",
    quoteKind = QuoteKind.CASH,
    sourceDisplayedAt = null,
    sourceQuoteId = "debug-notification-test",
    fetchedAt = Instant.now(),
)
