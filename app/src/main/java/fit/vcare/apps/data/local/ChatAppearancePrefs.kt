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
    private const val GLOBAL_KEY = "__global__"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getRawBackgroundUri(context: Context, key: String): String? =
        prefs(context).getString("bg_$key", null)

    private fun setRawBackgroundUri(context: Context, key: String, uri: String?) {
        prefs(context).edit().apply {
            if (uri.isNullOrBlank()) remove("bg_$key")
            else putString("bg_$key", uri)
        }.apply()
    }

    private fun getRawThemeKey(context: Context, key: String): String? =
        prefs(context).getString("theme_$key", null)

    private fun setRawThemeKey(context: Context, key: String, themeKey: String?) {
        prefs(context).edit().apply {
            if (themeKey.isNullOrBlank()) remove("theme_$key")
            else putString("theme_$key", themeKey)
        }.apply()
    }

    // ── API قبلی (سازگار با کدهای فعلی) — رفتار per-chat خام ──
    fun getBackgroundUri(context: Context, conversationId: String): String? =
        getRawBackgroundUri(context, conversationId)

    fun setBackgroundUri(context: Context, conversationId: String, uri: String?) =
        setRawBackgroundUri(context, conversationId, uri)

    fun getThemeKey(context: Context, conversationId: String): String =
        getRawThemeKey(context, conversationId) ?: ChatThemePresets.default.key

    fun setThemeKey(context: Context, conversationId: String, key: String) =
        setRawThemeKey(context, conversationId, key)

    // ── تنظیمات سراسری (پیش‌فرض همه‌ی چت‌ها) ──
    fun getGlobalBackgroundUri(context: Context): String? =
        getRawBackgroundUri(context, GLOBAL_KEY)

    fun setGlobalBackgroundUri(context: Context, uri: String?) =
        setRawBackgroundUri(context, GLOBAL_KEY, uri)

    fun getGlobalThemeKey(context: Context): String =
        getRawThemeKey(context, GLOBAL_KEY) ?: ChatThemePresets.default.key

    fun setGlobalThemeKey(context: Context, key: String) =
        setRawThemeKey(context, GLOBAL_KEY, key)

    // ── مقدار مؤثر: اول override اختصاصی چت، وگرنه سراسری ──
    fun getEffectiveBackgroundUri(context: Context, conversationId: String): String? =
        getRawBackgroundUri(context, conversationId) ?: getGlobalBackgroundUri(context)

    fun getEffectiveThemeKey(context: Context, conversationId: String): String =
        getRawThemeKey(context, conversationId) ?: getGlobalThemeKey(context)
}