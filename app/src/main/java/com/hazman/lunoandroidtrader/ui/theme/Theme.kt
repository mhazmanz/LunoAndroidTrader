package com.hazman.lunoandroidtrader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App-wide color definitions and theme setup for the Luno Android Trading Bot.
 *
 * This will give the app a professional dark-friendly look suitable for trading.
 * We can refine colors later if needed.
 */

// Basic color definitions (can be refined later)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00C48C),      // Teal-like primary
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF4C6FFF),    // Blue-ish accent
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF050608),   // Dark background
    onBackground = Color(0xFFE4E4E7),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0077FF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF00C48C),
    onSecondary = Color(0xFF000000),
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
)

/**
 * Root theme composable. Wraps all UI content.
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
        typography = Typography, // we will define Typography below
        shapes = Shapes,         // and Shapes below
        content = content
    )
}

/**
 * Simple typography setup.
 * For now, we use Material defaults. We can customise later if necessary.
 */
import androidx.compose.material3.Typography

val Typography = Typography()

/**
 * Simple shapes setup.
 * We can customise corner radii and more later if we want.
 */
import androidx.compose.material3.Shapes

val Shapes = Shapes()
