package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.login_system.getUid
//UidHelper.kt
fun getMyUidOrEmpty(context: Context): String {
    return getUid(context) ?: ""
}

/** برای FirestoreApiClient لازم است (Authorization: Bearer <token>) */
fun getMyTokenOrEmpty(context: Context): String {
    val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
    return prefs.getString("token", null) ?: ""
}

/** برای endpoint آپلود عکس موجود (aifitness-chatgpt/api/ai/upload-image) که با email کار می‌کند */
fun getMyEmailOrEmpty(context: Context): String {
    val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
    return prefs.getString("email", null) ?: ""
}