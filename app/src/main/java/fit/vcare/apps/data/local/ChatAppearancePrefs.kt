package fit.vcare.apps.data.local


import android.content.Context
import fit.vcare.apps.domain.model.ChatThemePresets

/**
 * ذخیره‌سازی محلی (فقط روی همین دستگاه) تنظیمات ظاهری هر Conversation:
 * پس‌زمینه‌ی چت و تم رنگی حباب پیام‌ها. این تنظیمات به سرور ارسال نمی‌شوند
 * و مخصوص همان چت روی همین دستگاه باقی می‌مانند.
 */
object ChatAppearancePrefs {

    private const val PREFS_NAME = "chat_appearance_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBackgroundUri(context: Context, conversationId: String): String? =
        prefs(context).getString("bg_$conversationId", null)

    fun setBackgroundUri(context: Context, conversationId: String, uri: String?) {
        prefs(context).edit().apply {
            if (uri.isNullOrBlank()) remove("bg_$conversationId")
            else putString("bg_$conversationId", uri)
        }.apply()
    }

    fun getThemeKey(context: Context, conversationId: String): String =
        prefs(context).getString("theme_$conversationId", ChatThemePresets.default.key)
            ?: ChatThemePresets.default.key

    fun setThemeKey(context: Context, conversationId: String, key: String) {
        prefs(context).edit().putString("theme_$conversationId", key).apply()
    }
}