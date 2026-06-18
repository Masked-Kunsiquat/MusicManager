package com.github.maskedkunisquat.musicmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LabelGold = Color(0xFFC8A97C)
private val LabelGoldDim = Color(0xFF8C7050)
private val SteelBlue = Color(0xFF7C9EC8)
private val DeviceBlack = Color(0xFF0D0D0D)
private val SurfaceDark = Color(0xFF1A1A1A)
private val SurfaceVariantDark = Color(0xFF242424)

private val DarkColors = darkColorScheme(
    primary = LabelGold,
    onPrimary = Color.Black,
    primaryContainer = LabelGoldDim,
    secondary = SteelBlue,
    background = DeviceBlack,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF333333)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
