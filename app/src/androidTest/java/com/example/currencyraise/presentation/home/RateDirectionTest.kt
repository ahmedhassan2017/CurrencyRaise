package com.example.currencyraise.presentation.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import com.example.currencyraise.domain.model.toObservation
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class RateDirectionTest {
    @get:Rule val compose = createComposeRule()
    private val previous = ExchangeRate(
        "USD", "EGP", "51.20".toBigDecimal(), "51.40".toBigDecimal(),
        "cib_ta3weem", "CIB via Ta3weem", "https://ta3weem.com", QuoteKind.BANK_RATE,
        null, null, Instant.parse("2026-09-13T10:00:00Z"),
    )
    private val current = previous.copy(
        buyRate = "51.22".toBigDecimal(), sellRate = "51.39".toBigDecimal(),
        fetchedAt = previous.fetchedAt.plusSeconds(3600),
    )
    private fun saved() = HomeUiState(
        rate = current, history = listOf(previous.toObservation(), current.toObservation()),
        loadingCache = false, loadingHistory = false, now = current.fetchedAt,
    )

    @Test fun mixedDirectionsAreAccessibleAndUnchangedCheckClearsArrows() {
        val state = mutableStateOf(saved())
        compose.setContent { CurrencyRaiseTheme { HomeScreen(state.value, {}, {}) } }
        compose.onNodeWithContentDescription("Bank buys: Up 0.02 EGP").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Bank sells: Down 0.01 EGP").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val next = current.copy(fetchedAt = current.fetchedAt.plusSeconds(3600))
            state.value = saved().copy(rate = next, history = saved().history + next.toObservation())
        }
        compose.onNodeWithContentDescription("Bank buys: No change").assertExists()
        compose.onNodeWithContentDescription("Bank sells: No change").assertExists()
        compose.onNodeWithContentDescription("Bank buys: Up 0.02 EGP").assertDoesNotExist()
    }

    @Test fun firstQuoteAndFailedHistoryExplainMissingComparison() {
        val state = mutableStateOf(saved().copy(history = listOf(current.toObservation())))
        compose.setContent { CurrencyRaiseTheme { HomeScreen(state.value, {}, {}) } }
        compose.onNodeWithContentDescription("Bank buys: No previous check to compare").assertExists()
        compose.runOnIdle { state.value = saved().copy(historyReadFailed = true) }
        compose.onNodeWithContentDescription("Bank buys: Comparison unavailable").assertExists()
        compose.onNodeWithContentDescription("Bank buys: Up 0.02 EGP").assertDoesNotExist()
    }

    @Test fun largeTextDarkThemeKeepsBothDirectionsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CurrencyRaiseTheme(darkTheme = true, dynamicColor = false) { HomeScreen(saved(), {}, {}) }
            }
        }
        compose.onNodeWithContentDescription("Bank buys: Up 0.02 EGP").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Bank sells: Down 0.01 EGP").performScrollTo().assertIsDisplayed()
    }
}
