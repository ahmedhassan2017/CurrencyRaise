package com.example.currencyraise.domain.model

enum class UpdateInterval(val hours: Int) {
    ONE_HOUR(1), TWO_HOURS(2), FOUR_HOURS(4), SIX_HOURS(6), TWELVE_HOURS(12), DAILY(24)
}

data class AppSettings(
    val updateInterval: UpdateInterval = UpdateInterval.ONE_HOUR,
    val automaticChecksEnabled: Boolean = true,
    // App preference only: Android permission/channel state is checked separately.
    val notificationsEnabled: Boolean = true,
)

enum class SettingsWriteResult { SAVED, STORAGE_FAILURE }
