package com.example.currencyraise.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.currencyraise.presentation.home.HomeRoute
import com.example.currencyraise.presentation.home.HomeViewModel
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.presentation.settings.SettingsRoute
import com.example.currencyraise.presentation.settings.SettingsViewModel

/** Two destinations with saved screen/scroll state; add a navigation library when routes grow. */
@Composable
fun CurrencyRaiseApp(initialBank: Bank? = null) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val screenState = rememberSaveableStateHolder()
    val home: HomeViewModel = viewModel()
    var initialBankApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialBank) {
        if (!initialBankApplied) {
            initialBank?.let(home::selectBank)
            initialBankApplied = true
        }
    }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    screenState.SaveableStateProvider(if (settingsOpen) "settings" else "home") {
        if (settingsOpen) {
            val settings: SettingsViewModel = viewModel()
            SettingsRoute(settings, onBack = { settingsOpen = false })
        } else {
            HomeRoute(home, onOpenSettings = { settingsOpen = true })
        }
    }
}
