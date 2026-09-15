package com.example.currencyraise.presentation.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.currencyraise.domain.model.RateObservation
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class RateHistoryChartTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val history = listOf(
        RateObservation(now.minusSeconds(72 * 3600), "51.00".toBigDecimal(), "51.10".toBigDecimal()),
        RateObservation(now.minusSeconds(12 * 3600), "51.20".toBigDecimal(), "51.30".toBigDecimal()),
        RateObservation(now.minusSeconds(6 * 3600), "51.15".toBigDecimal(), "51.25".toBigDecimal()),
        RateObservation(now, "51.30".toBigDecimal(), "51.40".toBigDecimal()),
    )

    private fun show(points: List<RateObservation> = history, dark: Boolean = false, scale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                CurrencyRaiseTheme(darkTheme = dark, dynamicColor = false) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            RateHistoryChart(points, now, Locale.US, ZoneOffset.UTC, 6, false, false, {})
                        }
                    }
                }
            }
        }
    }

    @Test fun dailyWeeklyAndPointInspectionUseTheActualObservations() {
        show()
        compose.onNodeWithText("Daily").assertIsSelected()
        compose.onNodeWithText("Up 0.10 EGP · Last 24 hours").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bank sells").assertDoesNotExist()
        compose.onNodeWithText("Previous point").performScrollTo().performClick()
        compose.onNodeWithText("51.15 EGP per USD").assertExists()
        compose.onNodeWithText("Weekly").performScrollTo().performClick()
        compose.onNodeWithText("Weekly").assertIsSelected()
        compose.onNodeWithText("Up 0.30 EGP · Last 7 days").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rate history").performScrollTo()
        capture("chart-weekly-light.png")
    }

    @Test fun emptyAndSinglePointStatesDoNotInventChanges() {
        show(points = listOf(history.last()))
        compose.onNodeWithText("One observation saved.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Previous point").assertDoesNotExist()
        compose.onNodeWithText("Next point").assertDoesNotExist()
        compose.onNodeWithText("Up", substring = true).assertDoesNotExist()
        compose.onNodeWithText("24h ago").assertExists()
        compose.onNodeWithText("Now").assertExists()
    }

    @Test fun emptyWindowExplainsHowToStartHistory() {
        show(points = emptyList())
        compose.onNodeWithText("No observations in this period yet.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Selected check:", substring = true).assertDoesNotExist()
    }

    @Test fun largeTextDarkChartKeepsControlsAndValuesReachable() {
        show(dark = true, scale = 2f)
        compose.onNodeWithText("Weekly").performScrollTo().performClick()
        compose.onNodeWithText("Previous point").performScrollTo().performClick()
        compose.onNodeWithText("51.15 EGP per USD").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Up 0.30 EGP · Last 7 days").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rate history").performScrollTo()
        capture("chart-weekly-dark-large-text.png")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
