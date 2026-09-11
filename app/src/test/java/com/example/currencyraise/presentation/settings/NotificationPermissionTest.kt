package com.example.currencyraise.presentation.settings

import com.example.currencyraise.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionTest {
    @Test fun neverAskedUsesExplicitRequestButDeniedUsesSettings() {
        val missing = NotificationAccess(false, false, true)
        assertEquals(PermissionAction.REQUEST, notificationAction(AppSettings(), missing))
        assertEquals(PermissionAction.OPEN_SETTINGS,
            notificationAction(AppSettings(notificationPermissionAsked = true), missing))
    }
    @Test fun appAndChannelBlocksAreIndependentOfSavedToggle() {
        val appBlocked = NotificationAccess(true, false, true)
        val channelBlocked = NotificationAccess(true, true, false)
        assertEquals(NotificationAccessStatus.APP_BLOCKED, appBlocked.status)
        assertEquals(NotificationAccessStatus.CHANNEL_BLOCKED, channelBlocked.status)
        assertEquals(PermissionAction.OPEN_SETTINGS, notificationAction(AppSettings(), appBlocked))
        assertEquals(PermissionAction.OPEN_SETTINGS, notificationAction(AppSettings(), channelBlocked))
    }
    @Test fun disabledAlertsOrAllowedSystemDoNotAsk() {
        assertEquals(PermissionAction.NONE,
            notificationAction(AppSettings(notificationsEnabled = false), NotificationAccess(false, false, true)))
        assertEquals(PermissionAction.NONE,
            notificationAction(AppSettings(), NotificationAccess(true, true, true)))
        assertEquals(PermissionAction.NONE, notificationAction(null, NotificationAccess(false, false, false)))
    }
    @Test fun runtimePermissionTakesPrecedenceOverAppOrChannelBlocking() {
        assertEquals(NotificationAccessStatus.PERMISSION_NEEDED, NotificationAccess(false, false, false).status)
    }
}
