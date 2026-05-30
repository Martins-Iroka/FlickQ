package com.martdev.flickq.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FlickQColorScheme = darkColorScheme(
    primary = FlickQColors.Gold,
    onPrimary = FlickQColors.OnGold,
    secondary = FlickQColors.GoldHighlight,
    onSecondary = FlickQColors.AlmostBlack,
    background = FlickQColors.AlmostBlack,
    onBackground = Color.White,
    surface = FlickQColors.SurfaceNavy,
    onSurface = Color.White,
    surfaceVariant = FlickQColors.DeepNavy,
    onSurfaceVariant = Color(0xFFB8C2D9),
    outline = FlickQColors.OutlineNavy,
    error = FlickQColors.Error,
    onError = Color.White
)

private val FlickQTypography = Typography()

@Composable
fun FlickQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlickQColorScheme,
        typography = FlickQTypography,
        content = content
    )
}
