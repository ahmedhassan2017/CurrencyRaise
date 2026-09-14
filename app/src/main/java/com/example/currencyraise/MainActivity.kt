package com.example.currencyraise

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.currencyraise.presentation.navigation.CurrencyRaiseApp
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import dagger.hilt.android.AndroidEntryPoint
import com.example.currencyraise.domain.model.Bank

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val initialBank = Bank.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_BANK) }
            CurrencyRaiseTheme { CurrencyRaiseApp(initialBank) }
        }
    }

    companion object { const val EXTRA_BANK = "com.example.currencyraise.BANK" }
}
