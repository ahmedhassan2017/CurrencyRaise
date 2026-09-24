package com.example.currencyraise.domain.model

enum class UpdateInterval(val minutes: Int) {
    FIVE_MINUTES(5),
    TWENTY_MINUTES(20),
    THIRTY_MINUTES(30),
    ONE_HOUR(60),
    TWO_HOURS(120),
    FOUR_HOURS(240),
    SIX_HOURS(360),
    TWELVE_HOURS(720),
    DAILY(1_440),
    ;

    val duration: java.time.Duration
        get() = java.time.Duration.ofMinutes(minutes.toLong())

    val wholeHours: Int?
        get() = minutes.takeIf { it % 60 == 0 }?.div(60)
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
