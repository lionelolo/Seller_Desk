package com.sellerdesk.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm paper-and-clay palette: calm, editorial, lots of whitespace.
private val LightColors = lightColorScheme(
    primary = Color(0xFFC96442),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E4DC),
    onPrimaryContainer = Color(0xFF6B2E18),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1F1E1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1E1D),
    surfaceVariant = Color(0xFFF0EEE6),
    onSurfaceVariant = Color(0xFF6B6A68),
    outline = Color(0xFFE3DFD3),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color(0xFF1F1E1D),
    primaryContainer = Color(0xFF4A2D22),
    onPrimaryContainer = Color(0xFFF6E4DC),
    background = Color(0xFF262624),
    onBackground = Color(0xFFF5F4EE),
    surface = Color(0xFF30302E),
    onSurface = Color(0xFFF5F4EE),
    surfaceVariant = Color(0xFF3A3937),
    onSurfaceVariant = Color(0xFFB8B5AA),
    outline = Color(0xFF4A4946),
    error = Color(0xFFF2B8B5),
)

private val Serif = FontFamily.Serif

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun SellerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
