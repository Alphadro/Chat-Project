package fit.vcare.apps.data.repository

import android.content.Context
//UidHelper.kt
fun getUid(context: Context): String? {
    val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
    return prefs.getString("uid", null)
}

fun getMyUidOrEmpty(context: Context): String {
    return getUid(context) ?: ""
}

/** برای FirestoreApiClient لازم است (Authorization: Bearer <token>) */
fun getMyTokenOrEmpty(context: Context): String {
    val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
    return prefs.getString("token", null) ?: ""
}

