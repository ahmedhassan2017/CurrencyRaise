package com.example.currencyraise.domain.model

enum class UpdateInterval(val hours: Int) {
    ONE_HOUR(1), TWO_HOURS(2), FOUR_HOURS(4), SIX_HOURS(6), TWELVE_HOURS(12), DAILY(24)
}

enum class AppearanceMode {
    SYSTEM, LIGHT, DARK;

    fun usesDarkColors(systemInDarkMode: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkMode
        LIGHT -> false
        DARK -> true
    }
}

data class AppSettings(
    val updateInterval: UpdateInterval = UpdateInterval.ONE_HOUR,
    val automaticChecksEnabled: Boolean = true,
    // App preference only: Android permission/channel state is checked separately.
    val notificationsEnabled: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
)

enum class SettingsWriteResult { SAVED, STORAGE_FAILURE }
