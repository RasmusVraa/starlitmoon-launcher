package ru.starlitmoon.launcher.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Rank labels/colors as on starlit-moon.ru (player-tags / player-badge). */
object PlayerRanks {
    data class Style(
        val id: String,
        val labelRu: String,
        val foreground: Color,
        val background: Brush,
        val border: Color,
    )

    private val order = listOf("admin", "mod", "shine", "sponsor")

    private val styles = mapOf(
        "admin" to Style(
            id = "admin",
            labelRu = "Админ",
            foreground = Color(0xFFFEF9C3),
            background = Brush.linearGradient(listOf(Color(0xEB7F1D1D), Color(0xF2450A0A))),
            border = Color(0xCCFBBF24),
        ),
        "mod" to Style(
            id = "mod",
            labelRu = "Модер",
            foreground = Color(0xFFD9F99D),
            background = Brush.linearGradient(listOf(Color(0xEB14532D), Color(0xF2064E3B))),
            border = Color(0xCCFBBF24),
        ),
        "sponsor" to Style(
            id = "sponsor",
            labelRu = "Спонсор",
            foreground = Color(0xFFFDE047),
            background = Brush.linearGradient(listOf(Color(0x2EEAB308), Color(0x2EEAB308))),
            border = Color(0x8CFACC15),
        ),
        "shine" to Style(
            id = "shine",
            labelRu = "Shine",
            foreground = Color(0xFFF5D0FE),
            background = Brush.linearGradient(listOf(Color(0x38A855F7), Color(0x38EC4899))),
            border = Color(0x80D946EF),
        ),
    )

    fun normalize(ranks: List<String>): List<Style> {
        val set = ranks.map { it.lowercase().trim() }.filter { it in styles }.toSet()
        return order.mapNotNull { id -> if (id in set) styles[id] else null }
    }

    fun highest(ranks: List<String>): Style? = normalize(ranks).firstOrNull()

    fun styleFor(raw: String): Style? {
        val id = raw.lowercase().trim()
        return styles[id] ?: Style(
            id = id,
            labelRu = raw,
            foreground = StarlitColors.Text,
            background = Brush.linearGradient(listOf(StarlitColors.PurpleMuted, StarlitColors.PurpleMuted)),
            border = StarlitColors.Purple.copy(alpha = 0.35f),
        ).takeIf { raw.isNotBlank() }
    }
}
