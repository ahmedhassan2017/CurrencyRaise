package com.example.currencyraise.presentation.market

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.currencyraise.R

enum class MarketTab { DOLLAR, GOLD }

@Composable
fun MarketTabs(
    selected: MarketTab,
    onSelect: (MarketTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tabShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(5.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MarketTab.entries.forEach { tab ->
                val isSelected = selected == tab
                val background by animateColorAsState(
                    targetValue = if (isSelected) colors.primary.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "Market tab background",
                )
                val border by animateColorAsState(
                    targetValue = if (isSelected) colors.primary.copy(alpha = 0.4f) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "Market tab border",
                )
                val foreground by animateColorAsState(
                    targetValue = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "Market tab content",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 52.dp)
                        .clip(tabShape)
                        .background(background)
                        .border(1.dp, border, tabShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { if (!isSelected) onSelect(tab) },
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(
                            if (tab == MarketTab.DOLLAR) R.drawable.ic_market_dollar
                            else R.drawable.ic_market_gold,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = foreground,
                    )
                    Text(
                        text = stringResource(
                            if (tab == MarketTab.DOLLAR) R.string.market_dollar else R.string.market_gold,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        color = foreground,
                    )
                }
            }
        }
    }
}
