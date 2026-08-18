package fit.vcare.apps.data.repository

import android.content.Context

/**
 * کش عمومی محلی (SharedPreferences) برای نگه‌داشتن آخرین اطلاعات دریافتی از سرور
 * تا موقع باز شدن مجدد صفحه، بدون معطلی (لودینگ سنگین) اطلاعات قبلی نمایش داده بشه
 * و همزمان در پس‌زمینه تلاش برای گرفتن اطلاعات تازه انجام بشه — دقیقاً مثل تلگرام.
 */
object LocalDataCache {

    private const val PREFS_NAME = "local_data_cache"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getString(context: Context, key: String): String? =
        prefs(context).getString(key, null)

    fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }
}