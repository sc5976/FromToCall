package com.fromtocall.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Набор пресетов акцентного цвета. Ключ — стабильный строковый идентификатор,
 * который хранится в DataStore, чтобы не ломать совместимость при смене UI.
 *
 * Каждый пресет задаёт три цвета:
 * - primary — основной (кнопки, акценты, таймер обратного отсчёта);
 * - primaryContainer — фон для больших выделений (карточка «Время ухода»);
 * - onPrimary — цвет текста/иконок поверх primary.
 */
data class AccentPreset(
    val key: String,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color
)

object AccentPalette {
    val presets: List<AccentPreset> = listOf(
        AccentPreset(
            key = "green",
            primary = Color(0xFF4CAF50),
            primaryContainer = Color(0xFF388E3C),
            onPrimary = Color(0xFFFFFFFF)
        ),
        AccentPreset(
            key = "blue",
            primary = Color(0xFF2196F3),
            primaryContainer = Color(0xFF1976D2),
            onPrimary = Color(0xFFFFFFFF)
        ),
        AccentPreset(
            key = "purple",
            primary = Color(0xFF9C27B0),
            primaryContainer = Color(0xFF7B1FA2),
            onPrimary = Color(0xFFFFFFFF)
        ),
        AccentPreset(
            key = "orange",
            primary = Color(0xFFFF9800),
            primaryContainer = Color(0xFFF57C00),
            onPrimary = Color(0xFF000000)
        ),
        AccentPreset(
            key = "teal",
            primary = Color(0xFF009688),
            primaryContainer = Color(0xFF00796B),
            onPrimary = Color(0xFFFFFFFF)
        ),
        AccentPreset(
            key = "red",
            primary = Color(0xFFE53935),
            primaryContainer = Color(0xFFC62828),
            onPrimary = Color(0xFFFFFFFF)
        )
    )

    fun byKey(key: String): AccentPreset =
        presets.firstOrNull { it.key == key } ?: presets.first()
}
