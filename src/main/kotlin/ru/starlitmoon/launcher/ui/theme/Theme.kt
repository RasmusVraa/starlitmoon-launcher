package ru.starlitmoon.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Flat, calm, premium palette. No gradients, no glass, no animated decoration.
 */
object StarlitColors {
    val Background = Color(0xFF07090F)
    val Surface = Color(0xFF121622)
    val SurfaceHover = Color(0xFF161C2B)
    val Border = Color(0xFF1E2433)
    val BorderStrong = Color(0xFF2A3245)

    val Gold = Color(0xFFD4A84B)
    val GoldMuted = Color(0x26D4A84B)
    val GoldDim = Color(0xFF9C7B37)
    val OnGold = Color(0xFF16130B)

    val Text = Color(0xFFEDEFF4)
    val TextMuted = Color(0xFF8A93A8)
    val TextDim = Color(0xFF565F73)

    val Online = Color(0xFF3ECE8A)
    val Offline = Color(0xFFE5636F)
}

object StarlitDimens {
    val Radius = 12.dp
    val RadiusSm = 10.dp
    val RadiusPill = 999.dp
    val WindowMinWidth = 1080.dp
    val WindowMinHeight = 680.dp
}

val StarlitTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        letterSpacing = (-0.3).sp,
        color = StarlitColors.Text,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        color = StarlitColors.Text,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = StarlitColors.Text,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = StarlitColors.TextMuted,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
        color = StarlitColors.Text,
    ),
)
