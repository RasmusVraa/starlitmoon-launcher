package ru.starlitmoon.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StarlitColors {
    val Void = Color(0xFF05070F)
    val BgDeep = Color(0xFF0B1020)
    val BgElevated = Color(0xFF12182C)
    val Glass = Color(0xB3141C32)
    val GlassStrong = Color(0xD9182238)
    val Stroke = Color(0x33A8B8E0)
    val StrokeSoft = Color(0x1AFFFFFF)
    val Text = Color(0xFFF2F4FA)
    val TextMuted = Color(0xFF8B98B8)
    val TextDim = Color(0xFF6A7694)
    val Gold = Color(0xFFE0B84A)
    val GoldDeep = Color(0xFFC9A227)
    val GoldSoft = Color(0x33E0B84A)
    val Violet = Color(0xFF7B6CF0)
    val VioletSoft = Color(0x337B6CF0)
    val Online = Color(0xFF3DDC97)
    val Offline = Color(0xFFFF6B7A)
    val Moon = Color(0xFFF7F3EA)
}

object StarlitDimens {
    val Radius = 16.dp
    val RadiusSm = 12.dp
    val RadiusPill = 999.dp
    val WindowMinWidth = 1080.dp
    val WindowMinHeight = 680.dp
}

val StarlitTitleGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFFFFFF), StarlitColors.Gold, StarlitColors.Violet),
)

val StarlitAccentGradient = Brush.horizontalGradient(
    colors = listOf(StarlitColors.GoldDeep, StarlitColors.Gold, StarlitColors.Violet),
)

val StarlitTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        letterSpacing = (-0.5).sp,
        color = StarlitColors.Text,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
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
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.6.sp,
        color = StarlitColors.Text,
    ),
)
