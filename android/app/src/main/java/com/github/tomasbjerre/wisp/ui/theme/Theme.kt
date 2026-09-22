package com.github.tomasbjerre.wisp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val WispGreen = Color(0xFF1B5E4F)
private val WispGreenLight = Color(0xFF4C8C7C)

private val LightColors =
    lightColorScheme(
        primary = WispGreen,
        secondary = WispGreenLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = WispGreenLight,
        secondary = WispGreen,
    )

@Composable
fun WispTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
