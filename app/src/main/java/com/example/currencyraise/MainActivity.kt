package com.example.currencyraise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.currencyraise.presentation.navigation.CurrencyRaiseApp
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurrencyRaiseTheme { CurrencyRaiseApp() }
        }
    }
}
