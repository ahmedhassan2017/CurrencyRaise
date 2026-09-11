package com.example.currencyraise.presentation.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.example.currencyraise.MainActivity
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsSurvivesActivityRecreationAndSystemBackReturnsHome() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Back to Home").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Back to Home").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("USD / EGP").assertIsDisplayed()
    }
}
