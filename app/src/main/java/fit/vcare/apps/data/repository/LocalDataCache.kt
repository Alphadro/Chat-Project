package fit.vcare.apps.data.repository

import android.content.Context

//LocalDataCache
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