package fit.vcare.apps.domain.model


import androidx.compose.ui.graphics.Color
//ChatAppearance.kt
data class ChatThemeOption(
    val key: String,
    val label: String,
    val lightColor: Color,
    val darkColor: Color
)

object ChatThemePresets {
    val default = ChatThemeOption(
        key = "default",
        label = "پیش‌فرض",
        lightColor = Color(0xFF6200EE),
        darkColor = Color(0xFF9A67EA)
    )
    val telegramBlue = ChatThemeOption(
        key = "telegram_blue",
        label = "آبی",
        lightColor = Color(0xFF3A8EE6),
        darkColor = Color(0xFF5AA9FF)
    )
    val whatsappGreen = ChatThemeOption(
        key = "whatsapp_green",
        label = "سبز",
        lightColor = Color(0xFF2E7D5B),
        darkColor = Color(0xFF4CAF7D)
    )
    val slateTeal = ChatThemeOption(
        key = "slate_teal",
        label = "فیروزه‌ای",
        lightColor = Color(0xFF33707A),
        darkColor = Color(0xFF4FA3AE)
    )
    val plum = ChatThemeOption(
        key = "plum",
        label = "بنفش",
        lightColor = Color(0xFF7A4B8A),
        darkColor = Color(0xFFA672B8)
    )
    val rose = ChatThemeOption(
        key = "rose",
        label = "رز",
        lightColor = Color(0xFFB5566F),
        darkColor = Color(0xFFD98098)
    )
    val slateGray = ChatThemeOption(
        key = "slate_gray",
        label = "خاکستری",
        lightColor = Color(0xFF546E7A),
        darkColor = Color(0xFF78909C)
    )

    val all = listOf(default, telegramBlue, whatsappGreen, slateTeal, plum, rose, slateGray)

    fun byKey(key: String): ChatThemeOption = all.firstOrNull { it.key == key } ?: default
}