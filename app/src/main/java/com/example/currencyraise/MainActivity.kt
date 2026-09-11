package com.example.currencyraise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.currencyraise.presentation.home.HomeRoute
import com.example.currencyraise.presentation.home.HomeViewModel
import com.example.currencyraise.ui.theme.CurrencyRaiseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurrencyRaiseTheme {
                val homeViewModel: HomeViewModel = viewModel()
                HomeRoute(homeViewModel)
            }
        }
    }
}
