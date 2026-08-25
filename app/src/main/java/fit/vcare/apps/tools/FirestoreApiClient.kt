package fit.vcare.apps.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
//FirestoreApiClient.kt
object FirestoreApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 })
        .build()

    private fun getToken(context: Context): String {
        val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
            ?: throw IllegalStateException("توکن یافت نشد — کاربر لاگین نشده")
        return "Bearer $token"
    }

    suspend fun read(context: Context, path: String): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val url = "${ApiConfig.BASE_URL}read-data?path=$path"

                Log.d("FirestoreApiClient", "─────────────────────────")
                Log.d("FirestoreApiClient", "URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    Log.d("FirestoreApiClient", "HTTP Code: ${response.code}")
                    Log.d("FirestoreApiClient", "Response Body: $body")

                    if (response.code == 401) {                      // ← جدید
                        clearInvalidToken(context)
                        SessionExpiryNotifier.notifyExpired()
                    }

                    if (!response.isSuccessful || body == null) {
                        Log.e("FirestoreApiClient", "ناموفق — کد: ${response.code}")
                        return@withContext null
                    }
                    JSONObject(body)
                }
            } catch (e: Exception) {
                Log.e("FirestoreApiClient", "Exception", e)
                null
            }
        }

    suspend fun write(context: Context, path: String, data: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("path", path)
                    put("data", data)
                }

                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("${ApiConfig.BASE_URL}write-data")
                    .post(body)
                    .addHeader("Authorization", getToken(context))
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("FirestoreApiClient", "Write HTTP Code: ${response.code}")
                    if (response.code == 401) {                      // ← جدید
                        clearInvalidToken(context)
                        SessionExpiryNotifier.notifyExpired()
                    }
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e("FirestoreApiClient", "Write Exception: ${e.message}")
                false
            }
        }

    // جایگزین تابع list قبلی که حدسی بود
    suspend fun list(
        context: Context,
        path: String,
        orderBy: String? = null,
        orderDesc: Boolean = false,
        limit: Int? = null,
        whereField: String? = null,
        whereOp: String? = null,
        whereValue: Long? = null
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context)
            val urlBuilder = StringBuilder("${ApiConfig.BASE_URL}read-data?path=$path")
            orderBy?.let {
                urlBuilder.append("&orderBy=").append(it)
                if (orderDesc) urlBuilder.append("&order=desc")
            }
            limit?.let { urlBuilder.append("&limit=").append(it) }
            if (whereField != null && whereOp != null && whereValue != null) {
                urlBuilder.append("&whereField=").append(whereField)
                urlBuilder.append("&whereOp=").append(java.net.URLEncoder.encode(whereOp, "UTF-8"))
                urlBuilder.append("&whereValue=").append(whereValue)
            }
            val url = urlBuilder.toString()

            Log.d("FirestoreApiClient", "List URL: $url")
            val request = Request.Builder().url(url).get().addHeader("Authorization", token).build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d("FirestoreApiClient", "List HTTP Code: ${response.code}")
                if (response.code == 401) {                          // ← جدید
                    clearInvalidToken(context)
                    SessionExpiryNotifier.notifyExpired()
                }
                if (!response.isSuccessful || body == null) return@withContext emptyList()
                val json = JSONObject(body)
                val docsArray = json.optJSONArray("documents") ?: return@withContext emptyList()
                (0 until docsArray.length()).map { docsArray.getJSONObject(it) }
            }
        } catch (e: Exception) {
            Log.e("FirestoreApiClient", "List Exception: ${e.message}")
            emptyList()
        }
    }
    private fun clearInvalidToken(context: Context) {
        val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("token")
            .putBoolean("isLoggedIn", false)
            .apply()
    }
    suspend fun insertAutoId(context: Context, collectionPath: String, data: JSONObject): String? =
        withContext(Dispatchers.IO) {
            try {
                val newId = java.util.UUID.randomUUID().toString()
                val docPath = "$collectionPath/$newId"
                val success = write(context, docPath, data)
                if (success) newId else null
            } catch (e: Exception) {
                Log.e("FirestoreApiClient", "InsertAutoId Exception: ${e.message}")
                null
            }
        }

    suspend fun delete(context: Context, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("path", path)
                }

                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("${ApiConfig.BASE_URL}delete-data")
                    .post(body)
                    .addHeader("Authorization", getToken(context))
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("FirestoreApiClient", "Delete HTTP Code: ${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e("FirestoreApiClient", "Delete Exception: ${e.message}")
                false
            }
        }


    suspend fun getServerTimeMillis(): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.appeks.com/aifitness-firebase/api/server-time")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d("FirestoreApiClient", "ServerTime raw: $body")
                val json = JSONObject(body ?: "{}")
                val ts = json.optLong("timestamp", 0L)
                if (ts > 0) ts else System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e("FirestoreApiClient", "ServerTime خطا: ${e.message} — از تایم دستگاه استفاده میشه")
            System.currentTimeMillis()
        }
    }


    fun formatTimeRemaining(deadlineMillis: Long, nowMillis: Long): String {
        val diff = deadlineMillis - nowMillis
        if (diff <= 0) return "منقضی شده"
        val totalMinutes = diff / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "✅ فعال — $hours ساعت و $minutes دقیقه مانده"
            hours > 0 -> "✅ فعال — $hours ساعت مانده"
            else -> "✅ فعال — $minutes دقیقه مانده"
        }
    }
    suspend fun increment(
        context: Context,
        path: String,
        fields: Map<String, Double>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fieldsJson = JSONObject().apply {
                fields.forEach { (key, amount) -> put(key, amount) }
            }
            val payload = JSONObject().apply {
                put("path", path)
                put("fields", fieldsJson)
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}increment")
                .post(body)
                .addHeader("Authorization", getToken(context))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d("FirestoreApiClient", "Increment HTTP Code: ${response.code}")
                Log.d("FirestoreApiClient", "Increment Response Body: $responseBody")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirestoreApiClient", "Increment Exception: ${e.message}")
            false
        }
    }

    /** حالت تک‌فیلدی، برای راحتی فراخوانی جاهایی که فقط یک فیلد رو increment می‌کنن */
    suspend fun increment(context: Context, path: String, field: String, amount: Double): Boolean =
        increment(context, path, mapOf(field to amount))
}


object ApiConfig {
    const val BASE_URL = "https://api.appeks.com/aifitness-firebase/api/firestore/"
}