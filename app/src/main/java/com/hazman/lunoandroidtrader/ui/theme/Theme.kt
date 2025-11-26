package com.hazman.lunoandroidtrader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App-wide theme for the Luno Android Trading Bot.
 *
 * This wraps all UI in a consistent Material3 theme with dark/light support.
 */

// Simple helper colors for text on primary/secondary.
private val ColorWhite = Color(0xFFFFFFFF)
private val ColorBlack = Color(0xFF000000)

// Dark theme color scheme
private val DarkColorScheme = darkColorScheme(
    primary = TealPrimary,
    onPrimary = ColorBlack,
    secondary = BlueSecondary,
    onSecondary = ColorWhite,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
)

// Light theme color scheme
private val LightColorScheme = lightColorScheme(
    primary = BlueSecondary,
    onPrimary = ColorWhite,
    secondary = TealPrimary,
    onSecondary = ColorBlack,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
)

/**
 * Root theme composable. Wrap this around all Composables in setContent.
 *
 * Usage:
 * LunoAndroidTraderTheme {
 *     // app UI goes here
 * }
 */
@Composable
fun LunoAndroidTraderTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // from Type.kt
        content = content
    )
}
