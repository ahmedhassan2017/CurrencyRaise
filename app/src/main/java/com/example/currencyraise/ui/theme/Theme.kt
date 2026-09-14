package com.example.currencyraise.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Amber500,
    onPrimary = Navy950,
    primaryContainer = ColorTokens.darkAmberContainer,
    onPrimaryContainer = Amber300,
    secondary = Periwinkle300,
    onSecondary = Navy900,
    secondaryContainer = Navy800,
    onSecondaryContainer = Ink50,
    tertiary = Gold400,
    onTertiary = Navy950,
    background = Navy950,
    onBackground = Ink50,
    surface = Navy900,
    onSurface = Ink50,
    surfaceVariant = Navy800,
    onSurfaceVariant = Ink400,
    surfaceContainer = Navy850,
    surfaceContainerHigh = Navy800,
    outline = Navy500,
    outlineVariant = Navy700,
    error = Danger400,
    onError = Navy950,
    errorContainer = ColorTokens.darkErrorContainer,
    onErrorContainer = ColorTokens.darkErrorText,
)

private val LightColorScheme = lightColorScheme(
    primary = LightAmber,
    onPrimary = ColorTokens.white,
    primaryContainer = ColorTokens.lightAmberContainer,
    onPrimaryContainer = ColorTokens.lightAmberText,
    secondary = LightNavy,
    onSecondary = ColorTokens.white,
    secondaryContainer = Cloud200,
    onSecondaryContainer = LightNavy,
    tertiary = ColorTokens.lightGold,
    onTertiary = LightNavy,
    background = Cloud50,
    onBackground = LightNavy,
    surface = ColorTokens.white,
    onSurface = LightNavy,
    surfaceVariant = Cloud100,
    onSurfaceVariant = ColorTokens.lightMuted,
    surfaceContainer = ColorTokens.white,
    surfaceContainerHigh = Cloud100,
    outline = ColorTokens.lightOutline,
    outlineVariant = Cloud200,
    error = ColorTokens.lightError,
    onError = ColorTokens.white,
    errorContainer = ColorTokens.lightErrorContainer,
    onErrorContainer = ColorTokens.lightErrorText,
)

private object ColorTokens {
    val white = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val darkAmberContainer = androidx.compose.ui.graphics.Color(0xFF4A302D)
    val darkErrorContainer = androidx.compose.ui.graphics.Color(0xFF461F2A)
    val darkErrorText = androidx.compose.ui.graphics.Color(0xFFFFD9DC)
    val lightAmberContainer = androidx.compose.ui.graphics.Color(0xFFFFE2C4)
    val lightAmberText = androidx.compose.ui.graphics.Color(0xFF321500)
    val lightGold = androidx.compose.ui.graphics.Color(0xFF735C00)
    val lightMuted = androidx.compose.ui.graphics.Color(0xFF58647D)
    val lightOutline = androidx.compose.ui.graphics.Color(0xFF78849E)
    val lightError = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
    val lightErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
    val lightErrorText = androidx.compose.ui.graphics.Color(0xFF410002)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CurrencyRaiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = rememberAppTypography(),
        shapes = AppShapes,
        content = content,
    )
}
