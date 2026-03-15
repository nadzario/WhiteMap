package com.example.whitemap.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = RedPrimary,
    onPrimary = Color.White,
    background = WhiteBackground,
    onBackground = BlackText,
    surface = WhiteBackground,
    onSurface = BlackText,
    surfaceVariant = GrayLight,
    onSurfaceVariant = BlackText
)

@Composable
fun WhiteMapTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
