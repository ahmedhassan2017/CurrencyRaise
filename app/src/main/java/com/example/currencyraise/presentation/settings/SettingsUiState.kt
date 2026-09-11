package com.example.currencyraise.presentation.settings

import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.NotificationAccess
import com.example.currencyraise.domain.model.NotificationAccessStatus

data class SettingsUiState(
    val settings: AppSettings? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val readFailed: Boolean = false,
    val message: SettingsMessage? = null,
) {
    val editable: Boolean get() = settings != null && !loading && !saving && !readFailed
}

enum class SettingsMessage { SAVED, WRITE_FAILED }
enum class PermissionAction { NONE, REQUEST, OPEN_SETTINGS }

internal fun notificationAction(settings: AppSettings?, access: NotificationAccess): PermissionAction = when {
    settings == null || !settings.notificationsEnabled -> PermissionAction.NONE
    access.status == NotificationAccessStatus.ALLOWED -> PermissionAction.NONE
    !access.permissionGranted && !settings.notificationPermissionAsked -> PermissionAction.REQUEST
    else -> PermissionAction.OPEN_SETTINGS
}
