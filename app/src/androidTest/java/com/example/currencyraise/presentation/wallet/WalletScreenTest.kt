package com.example.currencyraise.presentation.wallet

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.model.ExchangeRate
import com.example.currencyraise.domain.model.QuoteKind
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WalletScreenTest {
    @get:Rule val compose = createComposeRule()

    private val rate = ExchangeRate(
        baseCurrency = "USD",
        quoteCurrency = "EGP",
        buyRate = BigDecimal("50.00"),
        sellRate = BigDecimal("55.00"),
        sourceId = "cib_ta3weem",
        sourceName = "CIB via Ta3weem",
        sourceUrl = "https://ta3weem.com/",
        quoteKind = QuoteKind.BANK_RATE,
        sourceDisplayedAt = LocalDateTime.parse("2026-09-16T12:00:00"),
        sourceQuoteId = "test-rate",
        fetchedAt = Instant.parse("2026-09-16T12:00:00Z"),
    )

    private fun state() = WalletUiState(
        bank = Bank.CIB,
        savedBalance = SavingsBalance(
            usd = BigDecimal("100"),
            egp = BigDecimal("5000"),
        ),
        usdInput = "100",
        egpInput = "5000",
        loadingSavings = false,
        rate = rate,
        loadingRate = false,
        loadingHistory = false,
        now = Instant.parse("2026-09-16T12:30:00Z"),
    )

    @Test fun mixedSavingsShowOnlyTheEgpEquivalentWithoutPublishingSellRate() {
        var selectedBank: Bank? = null
        compose.setContent {
            CurrencyRaiseTheme {
                WalletScreen(
                    state = state(),
                    onOpenSettings = {},
                    onSelectBank = { selectedBank = it },
                    onUsdInput = {},
                    onEgpInput = {},
                    onSave = {},
                    onRetrySavings = {},
                    onRefreshRate = {},
                )
            }
        }

        compose.onNodeWithText("10,000.00 EGP").assertIsDisplayed()
        compose.onNodeWithText("Current balance in USD").assertDoesNotExist()
        compose.onNodeWithText("55.00").assertDoesNotExist()
        compose.onNodeWithText("Banque Misr").performClick()
        compose.runOnIdle { assertEquals(Bank.BANQUE_MISR, selectedBank) }
    }

    @Test fun zeroDisappearsWhileAnAmountFieldIsFocused() {
        val ui = mutableStateOf(
            state().copy(
                savedBalance = SavingsBalance.EMPTY,
                usdInput = "0",
                egpInput = "0",
            ),
        )
        compose.setContent {
            CurrencyRaiseTheme {
                WalletScreen(
                    state = ui.value,
                    onOpenSettings = {},
                    onSelectBank = {},
                    onUsdInput = { ui.value = ui.value.copy(usdInput = it) },
                    onEgpInput = { ui.value = ui.value.copy(egpInput = it) },
                    onSave = {},
                    onRetrySavings = {},
                    onRefreshRate = {},
                )
            }
        }

        compose.onNodeWithText("USD savings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("", ui.value.usdInput) }
        compose.onNodeWithText("EGP savings").performClick()
        compose.runOnIdle {
            assertEquals("0", ui.value.usdInput)
            assertEquals("", ui.value.egpInput)
        }
    }

    @Test fun editedSavingsCanBeSavedFromEitherCurrencyField() {
        val ui = mutableStateOf(state())
        var saved = 0
        compose.setContent {
            CurrencyRaiseTheme {
                WalletScreen(
                    state = ui.value,
                    onOpenSettings = {},
                    onSelectBank = {},
                    onUsdInput = { ui.value = ui.value.copy(usdInput = it) },
                    onEgpInput = { ui.value = ui.value.copy(egpInput = it) },
                    onSave = { saved++ },
                    onRetrySavings = {},
                    onRefreshRate = {},
                )
            }
        }

        compose.onNodeWithText("USD savings").performScrollTo().performTextReplacement("250")
        compose.onNodeWithText("EGP savings").performTextReplacement("7500")
        compose.onNodeWithText("Save savings").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("250", ui.value.usdInput)
            assertEquals("7500", ui.value.egpInput)
            assertEquals(1, saved)
        }
    }
}
