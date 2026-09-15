package com.example.currencyraise.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.currencyraise.R
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.presentation.home.HomeRoute
import com.example.currencyraise.presentation.home.HomeViewModel
import com.example.currencyraise.presentation.settings.SettingsRoute
import com.example.currencyraise.presentation.settings.SettingsViewModel
import com.example.currencyraise.presentation.wallet.WalletRoute
import com.example.currencyraise.presentation.wallet.WalletViewModel
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRouteKey : NavKey

@Serializable
private data object WalletRouteKey : NavKey

@Serializable
private data object SettingsRouteKey : NavKey

private enum class MainTab { HOME, WALLET }

@Composable
fun CurrencyRaiseApp(initialBank: Bank? = null) {
    val backStack = rememberNavBackStack(HomeRouteKey)
    val home: HomeViewModel = viewModel()
    val wallet: WalletViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()
    var initialBankApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialBank) {
        if (!initialBankApplied) {
            initialBank?.let(home::selectBank)
            initialBankApplied = true
        }
    }

    val currentDestination = backStack.lastOrNull()
    val selectedTab = remember(backStack.toList()) {
        if (backStack.any { it == WalletRouteKey }) MainTab.WALLET else MainTab.HOME
    }
    val showBottomBar = currentDestination != SettingsRouteKey

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == MainTab.HOME,
                        onClick = {
                            if (currentDestination != HomeRouteKey) {
                                backStack.clear()
                                backStack.add(HomeRouteKey)
                            }
                        },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_home),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.tab_home)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.WALLET,
                        onClick = {
                            if (currentDestination != WalletRouteKey) {
                                backStack.clear()
                                backStack.add(HomeRouteKey)
                                backStack.add(WalletRouteKey)
                            }
                        },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_wallet),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.tab_wallet)) },
                    )
                }
            }
        },
    ) { insets ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .padding(insets)
                .consumeWindowInsets(insets),
            entryProvider = entryProvider {
                entry<HomeRouteKey> {
                    HomeRoute(
                        viewModel = home,
                        onOpenSettings = { backStack.add(SettingsRouteKey) },
                    )
                }
                entry<WalletRouteKey> {
                    WalletRoute(
                        viewModel = wallet,
                        onOpenSettings = { backStack.add(SettingsRouteKey) },
                    )
                }
                entry<SettingsRouteKey> {
                    SettingsRoute(
                        viewModel = settings,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}
