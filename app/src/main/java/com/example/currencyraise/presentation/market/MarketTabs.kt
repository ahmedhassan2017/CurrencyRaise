package com.example.currencyraise.presentation.market

import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.currencyraise.R

enum class MarketTab { DOLLAR, GOLD }

@Composable
fun MarketTabs(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        MarketTab.entries.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painter = painterResource(
                            if (tab == MarketTab.DOLLAR) R.drawable.ic_market_dollar
                            else R.drawable.ic_market_gold,
                        ),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(stringResource(if (tab == MarketTab.DOLLAR) R.string.market_dollar else R.string.market_gold))
                },
            )
        }
    }
}
