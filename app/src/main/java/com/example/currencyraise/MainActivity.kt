package com.example.currencyraise

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.currencyraise.domain.model.AppearanceMode
import com.example.currencyraise.presentation.navigation.CurrencyRaiseApp
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import dagger.hilt.android.AndroidEntryPoint
import com.example.currencyraise.domain.model.Bank
import com.example.currencyraise.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val initialBank = Bank.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_BANK) }
            val appearanceFlow = remember(settingsRepository) {
                settingsRepository.observeSettings()
                    .map { it.appearanceMode }
                    .catch { emit(AppearanceMode.SYSTEM) }
            }
            val appearance by appearanceFlow.collectAsStateWithLifecycle(AppearanceMode.SYSTEM)
            val darkTheme = appearance.usesDarkColors(isSystemInDarkTheme())
            SideEffect {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CurrencyRaiseTheme(darkTheme = darkTheme) { CurrencyRaiseApp(initialBank) }
        }
    }

    companion object { const val EXTRA_BANK = "com.example.currencyraise.BANK" }
}
