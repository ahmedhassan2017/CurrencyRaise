package com.example.currencyraise.presentation.settings

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.currencyraise.domain.model.*
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val missing = NotificationAccess(false, false, true)

    @Test fun permissionIsOnlyRequestedByExplicitAction() {
        var requests = 0
        compose.setContent {
            CurrencyRaiseTheme {
                SettingsScreen(SettingsUiState(AppSettings(), loading = false), missing,
                    {}, {}, {}, {}, {}, { requests++ })
            }
        }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Allow notifications").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("Send test notification").assertDoesNotExist()
    }

    @Test fun denialOffersSettingsAndDoesNotRepeatPrompt() {
        compose.setContent {
            CurrencyRaiseTheme {
                SettingsScreen(SettingsUiState(AppSettings(notificationPermissionAsked = true), loading = false),
                    missing, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Allow notifications").assertDoesNotExist()
        compose.onNodeWithText("Open Android notification settings").performScrollTo().assertIsDisplayed()
    }

    @Test fun intervalChoiceReturnsSelectedValue() {
        var interval: UpdateInterval? = null
        compose.setContent {
            CurrencyRaiseTheme {
                SettingsScreen(SettingsUiState(AppSettings(), loading = false), missing,
                    {}, { interval = it }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Check interval: 1 hour").performScrollTo().performClick()
        compose.onNodeWithText("Every 6 hours").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(UpdateInterval.SIX_HOURS, interval) }
    }

    @Test fun togglesRemainIndependent() {
        var selected = AppSettings()
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings()) }
            CurrencyRaiseTheme {
                SettingsScreen(SettingsUiState(settings, loading = false), missing, {}, {},
                    { settings = settings.copy(automaticChecksEnabled = it); selected = settings },
                    { settings = settings.copy(notificationsEnabled = it); selected = settings }, {}, {})
            }
        }
        compose.onNodeWithText("Automatic checks").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(selected.automaticChecksEnabled)
            assertTrue(selected.notificationsEnabled)
        }
        compose.onNodeWithText("Check interval: 1 hour").assertIsNotEnabled()
    }

    @Test fun debugNotificationToolReportsSuccessfulDelivery() {
        var sends = 0
        compose.setContent {
            CurrencyRaiseTheme {
                SettingsScreen(
                    state = SettingsUiState(AppSettings(), loading = false),
                    access = NotificationAccess(true, true, true),
                    onBack = {}, onInterval = {}, onAutomatic = {}, onNotifications = {},
                    onRetry = {}, onPermissionAction = {}, showTestNotification = true,
                    onSendTestNotification = { sends++; true },
                )
            }
        }
        compose.onNodeWithText("Send test notification").performScrollTo().performClick()
        compose.onNodeWithText("Test notification sent.", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, sends) }
    }

    @Test fun languagePickerOffersSystemEnglishAndArabic() {
        var selected: String? = null
        compose.setContent {
            CurrencyRaiseTheme {
                SettingsScreen(
                    state = SettingsUiState(AppSettings(), loading = false),
                    access = missing,
                    onBack = {}, onInterval = {}, onAutomatic = {}, onNotifications = {},
                    onRetry = {}, onPermissionAction = {},
                    languageTag = "en", onLanguage = { selected = it },
                )
            }
        }
        compose.onNodeWithText("App language").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.onNodeWithText("Follow system").assertExists()
        compose.onNodeWithText("العربية").performClick()
        compose.runOnIdle { assertEquals("ar", selected) }
    }
}
