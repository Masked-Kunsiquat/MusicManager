package com.github.maskedkunisquat.musicmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.maskedkunisquat.musicmanager.R

private val LabelGold = Color(0xFFC8A97C)
private val LabelGoldDim = Color(0xFF8C7050)
private val RetroGreen = Color(0xFF6BA368)
private val DeviceBlack = Color(0xFF0D0D0D)
private val SurfaceDark = Color(0xFF1A1A1A)
private val SurfaceVariantDark = Color(0xFF242424)

private val DarkColors = darkColorScheme(
    primary = LabelGold,
    onPrimary = Color.Black,
    primaryContainer = LabelGoldDim,
    secondary = RetroGreen,
    onSecondary = Color.Black,
    background = DeviceBlack,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF333333)
)

private val RetroShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

private val mono = FontFamily(Font(R.font.nokia_cellphone))

// Font must be used at 8sp or exact multiples (16, 24, 32) — bitmap-emulating, no antialias.
private val RetroTypography = Typography().run {
    copy(
        displayLarge  = displayLarge.copy(fontFamily = mono, fontSize = 32.sp),
        displayMedium = displayMedium.copy(fontFamily = mono, fontSize = 32.sp),
        displaySmall  = displaySmall.copy(fontFamily = mono, fontSize = 32.sp),
        headlineLarge  = headlineLarge.copy(fontFamily = mono, fontSize = 32.sp),
        headlineMedium = headlineMedium.copy(fontFamily = mono, fontSize = 24.sp),
        headlineSmall  = headlineSmall.copy(fontFamily = mono, fontSize = 24.sp),
        titleLarge  = titleLarge.copy(fontFamily = mono, fontSize = 24.sp),
        titleMedium = titleMedium.copy(fontFamily = mono, fontSize = 16.sp),
        titleSmall  = titleSmall.copy(fontFamily = mono, fontSize = 16.sp),
        bodyLarge  = bodyLarge.copy(fontFamily = mono, fontSize = 16.sp),
        bodyMedium = bodyMedium.copy(fontFamily = mono, fontSize = 16.sp),
        bodySmall  = bodySmall.copy(fontFamily = mono, fontSize = 8.sp),
        labelLarge  = labelLarge.copy(fontFamily = mono, fontSize = 16.sp),
        labelMedium = labelMedium.copy(fontFamily = mono, fontSize = 16.sp),
        labelSmall  = labelSmall.copy(fontFamily = mono, fontSize = 8.sp),
    )
}

@Composable
fun RetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = RetroTypography,
        shapes = RetroShapes,
        content = content
    )
}
