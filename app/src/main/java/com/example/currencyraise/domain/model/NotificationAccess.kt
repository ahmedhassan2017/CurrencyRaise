package com.example.currencyraise.domain.model

data class NotificationAccess(
    val permissionGranted: Boolean,
    val appEnabled: Boolean,
    val channelEnabled: Boolean,
) {
    val status: NotificationAccessStatus
        get() = when {
            !permissionGranted -> NotificationAccessStatus.PERMISSION_NEEDED
            !appEnabled -> NotificationAccessStatus.APP_BLOCKED
            !channelEnabled -> NotificationAccessStatus.CHANNEL_BLOCKED
            else -> NotificationAccessStatus.ALLOWED
        }
}

enum class NotificationAccessStatus { PERMISSION_NEEDED, APP_BLOCKED, CHANNEL_BLOCKED, ALLOWED }
