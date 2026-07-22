package ru.starlitmoon.launcher.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StarlitColors {
    val BgDeep = Color(0xFF0A0E1A)
    val BgCard = Color(0xD912182A)
    val CardBorder = Color(0x26788CDC)
    val Text = Color(0xFFE8ECF8)
    val TextMuted = Color(0xFF9AA8C8)
    val Accent = Color(0xFFC9A227)
    val AccentGlow = Color(0x59C9A227)
    val Purple = Color(0xFF6B5CE7)
    val PurpleSoft = Color(0x406B5CE7)
    val Online = Color(0xFF4ADE80)
    val Offline = Color(0xFFF87171)
    val Moon = Color(0xFFF4F0E6)
}

object StarlitDimens {
    val Radius = 14.dp
    val WindowMinWidth = 980.dp
    val WindowMinHeight = 640.dp
}

val StarlitTitleGradient = Brush.linearGradient(
    colors = listOf(StarlitColors.Text, StarlitColors.Accent, StarlitColors.Purple),
)

val StarlitFontFamily = FontFamily.SansSerif

val StarlitTypography = androidx.compose.material3.Typography(
    headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(
        fontFamily = StarlitFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
    ),
    titleMedium = androidx.compose.material3.Typography().titleMedium.copy(
        fontFamily = StarlitFontFamily,
        fontWeight = FontWeight.Bold,
        color = StarlitColors.Text,
    ),
    bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(
        fontFamily = StarlitFontFamily,
        color = StarlitColors.TextMuted,
    ),
    labelLarge = androidx.compose.material3.Typography().labelLarge.copy(
        fontFamily = StarlitFontFamily,
        fontWeight = FontWeight.SemiBold,
    ),
)
