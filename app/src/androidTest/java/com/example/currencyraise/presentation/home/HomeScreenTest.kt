package com.example.currencyraise.presentation.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.currencyraise.domain.model.AppSettings
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val sample = ExchangeRate(
        "USD", "EGP", BigDecimal("51.27"), BigDecimal("51.37"),
        "banque_misr", "Banque Misr", "https://www.banquemisr.com/",
        QuoteKind.CASH, LocalDateTime.parse("2026-09-10T14:28:09"),
        "2026091015", Instant.parse("2026-09-11T10:00:00Z"),
    )
    private fun saved() = HomeUiState(
        bank = Bank.BANQUE_MISR,
        rate = sample, settings = AppSettings(), loadingCache = false,
        now = Instant.parse("2026-09-11T10:30:00Z"),
    )

    @Test fun bankSelectorUpdatesSelectionAndCibAttributionAndSourceLink() {
        val state = mutableStateOf(saved())
        var opened = ""
        compose.setContent {
            CurrencyRaiseTheme {
                HomeScreen(state.value, {}, { opened = it }, onSelectBank = { bank ->
                    state.value = HomeUiState(bank = bank, loadingCache = false)
                })
            }
        }
        compose.onNodeWithText("CIB", useUnmergedTree = false).performClick()
        compose.onNodeWithText("CIB").assertIsSelected()
        compose.onNodeWithText("CIB via Ta3weem · Bank rates").assertExists()
        compose.onNodeWithText("Rate details").performScrollTo().performClick()
        compose.onNodeWithText("CIB rates supplied by Ta3weem", substring = true).assertExists()
        compose.onNodeWithText("51.27").assertDoesNotExist()
        compose.onNodeWithText("View CIB via Ta3weem rate source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Bank.CIB.sourceUrl, opened) }
    }

    @Test fun failedRefreshRetainsRatesAndRetryWorks() {
        var clicks = 0
        compose.setContent {
            CurrencyRaiseTheme {
                HomeScreen(saved().copy(refreshError = HomeError.NETWORK), { clicks++ }, {})
            }
        }
        compose.onNodeWithText("51.27").assertExists()
        compose.onNodeWithText("51.37").assertExists()
        compose.onNodeWithText("Showing your last saved rates.").assertExists()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun calculatorUsesCorrectRateSideAndFollowsSelectedBank() {
        val state = mutableStateOf(saved())
        compose.setContent {
            CurrencyRaiseTheme {
                HomeScreen(
                    state = state.value,
                    onRefresh = {},
                    onOpenSource = {},
                    onSelectBank = { bank ->
                        state.value = state.value.copy(
                            bank = bank,
                            rate = sample.copy(
                                buyRate = BigDecimal("50.00"),
                                sellRate = BigDecimal("55.00"),
                                sourceId = "cib_ta3weem",
                                sourceName = "CIB via Ta3weem",
                            ),
                        )
                    },
                )
            }
        }

        compose.onNodeWithText("Amount").performScrollTo().performTextInput("10")
        compose.onNodeWithText("512.70 EGP").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("EGP to USD").performScrollTo().performClick()
        compose.onNodeWithText("Amount").performTextClearance()
        compose.onNodeWithText("Amount").performTextInput("513.70")
        compose.onNodeWithText("10.00 USD").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("CIB").performScrollTo().performClick()
        compose.onNodeWithText("9.34 USD").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Using CIB bank sell rate: 55.00 EGP per USD.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun loadingDisablesRefreshAndDoesNotInventPrices() {
        compose.setContent { CurrencyRaiseTheme { HomeScreen(HomeUiState(), {}, {}) } }
        compose.onNodeWithText("Getting your first quote").assertExists()
        compose.onNodeWithText("Refresh rates").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("51.27").assertDoesNotExist()
    }

    @Test fun largeTextDarkThemeCanReachBothRatesAndSource() {
        var opened = ""
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CurrencyRaiseTheme(darkTheme = true, dynamicColor = false) {
                    HomeScreen(saved(), {}, { opened = it })
                }
            }
        }
        compose.onNodeWithText("51.27").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("51.37").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rate details").performScrollTo().performClick()
        compose.onNodeWithText("Source display time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View Banque Misr rate source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(sample.sourceUrl, opened) }
    }

    @Test fun storageHelpExplainsResetWithoutErasingAnything() {
        compose.setContent {
            CurrencyRaiseTheme { HomeScreen(saved().copy(rateReadFailed = true), {}, {}) }
        }
        compose.onNodeWithText("Storage help").performScrollTo().performClick()
        compose.onNodeWithText("The app never resets them automatically.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("51.27").assertExists()
    }

    @Test fun secondaryInformationIsHiddenUntilRequested() {
        compose.setContent { CurrencyRaiseTheme { HomeScreen(saved(), {}, {}) } }
        compose.onNodeWithText("51.27").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("51.37").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Source display time").assertDoesNotExist()
        compose.onNodeWithText("Daily").assertDoesNotExist()
        compose.onNodeWithText("Show rate history").performScrollTo().performClick()
        compose.onNodeWithText("Daily").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hide rate history").performScrollTo().performClick()
        compose.onNodeWithText("Daily").assertDoesNotExist()
        compose.onNodeWithText("Rate details").performScrollTo().performClick()
        compose.onNodeWithText("Source display time").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Source display time").assertDoesNotExist()
    }
}
