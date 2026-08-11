package fit.vcare.apps.login_system

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.tools.FirestoreApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.provider.Settings

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _loginSuccess = MutableStateFlow(false)

    private val context = application.applicationContext

    fun setLoading(value: Boolean) {
        _isLoading.value = value
    }

    fun loginWithGoogleIdToken(
        idToken: String,
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isLoading.value = true

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.appeks.com/aifitness-firebase/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(AuthApi::class.java)

        viewModelScope.launch {
            val result: kotlin.Result<GoogleLoginResponse> = try {
                Log.d("VCareLogin", "شروع درخواست لاگین -> طول idToken=${idToken.length}")
                val response = api.loginWithGoogle(GoogleLoginRequest(idToken))
                Log.d("VCareLogin", "پاسخ سرور دریافت شد -> httpCode=${response.code()} isSuccessful=${response.isSuccessful}")

                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Log.d("VCareLogin", "بدنه پاسخ: $body")
                    kotlin.Result.success(body)
                } else {
                    val err = response.errorBody()?.string()
                    Log.e("VCareLogin", "بک‌اند خطا داد -> httpCode=${response.code()} body=$err")
                    kotlin.Result.failure(Exception(err ?: "Backend error (${response.code()})"))
                }
            } catch (e: Exception) {
                Log.e("VCareLogin", "Exception حین درخواست شبکه", e)
                kotlin.Result.failure(e)
            }

            result.onSuccess { body ->
                val uid = body.user.uid
                val email = body.user.email
                Log.d("VCareLogin", "لاگین موفق -> uid=$uid email=$email")

                val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("isLoggedIn", true)
                    .putString("uid", uid)
                    .putString("email", email)
                    .putString("user_email", email)
                    .putString("token", body.token)
                    .apply()

                _loginSuccess.value = true
                createUserProfileIfNeeded(context, uid, email)
                _isLoading.value = false

                try {
                    Log.d("VCareLogin", "onSuccess() فراخوانی می‌شود")
                    onSuccess()
                } catch (navEx: Exception) {
                    Log.e("VCareLogin", "لاگین و ساخت پروفایل موفق بود، ولی onSuccess/ناوبری خطا داد", navEx)
                }
            }.onFailure { e ->
                _isLoading.value = false
                Log.e("VCareLogin", "لاگین ناموفق بود", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun getUid(context: Context): String? {
        val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        return prefs.getString("uid", null)
    }


    suspend fun checkUserStatus(onFinished: () -> Unit): String {
        val uid = getUid(context)
        val isIntroSeen = true
        if (!isIntroSeen) {
            return "lang_first"
        }

        if (uid.isNullOrEmpty()) {
            return "login"
        }

        return try {
            val readUrl = "${ApiFirebase.FIRSTFIRE_URL}read?path=user/$uid"

            val responseJson = DataSyncManager.fetchAndCacheData(
                context = context,
                cacheKey = "user_info_$uid",
                url = readUrl
            )

            val document = responseJson?.optJSONObject("document")
            if (document == null) {
                return "additionalInfo"
            }

            val storedName = document.optString("name")

            if (!storedName.isNullOrEmpty()) {
                onFinished()
                "page1"
            } else {
                "additionalInfo"
            }

        } catch (e: Exception) {
            "additionalInfo"
        }
    }
    private fun createUserProfileIfNeeded(
        context: Context,
        uid: String,
        email: String
    ) {
        viewModelScope.launch {
            try {
                Log.d("VCareLogin", "createUserProfileIfNeeded شروع شد -> uid=$uid")

                val rawExisting = FirestoreApiClient.read(context, "users/$uid")
                val existing = if (rawExisting != null && !rawExisting.has("error")) {
                    rawExisting.optJSONObject("document") ?: rawExisting
                } else null
                Log.d("VCareLogin", "createUserProfileIfNeeded -> سند فعلی: $existing")

                if (existing != null && existing.has("uid")) {
                    Log.d("VCareLogin", "پروفایل کاربر قبلاً وجود داره -> nova=${existing.optInt("nova", -1)}")
                    return@launch
                }

                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )

                val userProfile = JSONObject().apply {
                    put("email", email)
                    put("deviceId", deviceId)
                    put("nova", 250)
                    put("isPermanent", false)
                    put("trialUsed", false)
                    put("createdAt", System.currentTimeMillis())
                    put("lastActiveAt", System.currentTimeMillis())
                }

                val success = FirestoreApiClient.write(context, "users/$uid", userProfile)

                if (success) {
                    Log.d("AuthViewModel", "پروفایل کاربر با موفقیت ساخته شد — uid: $uid")
                } else {
                    Log.e("AuthViewModel", "خطا در ساخت پروفایل کاربر")
                }

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Exception در createUserProfile: ${e.message}")
            }
        }
    }
}

data class GoogleLoginRequest(val idToken: String)

data class GoogleLoginResponse(
    val message: String,
    val newUser: Boolean,
    val token: String,
    val user: UserInfo
)

data class UserInfo(
    val uid: String,
    val email: String
)

interface AuthApi {
    @POST("api/auth/google")
    suspend fun loginWithGoogle(@Body body: GoogleLoginRequest): Response<GoogleLoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<Unit>
}

object ApiFirebase {
    const val FIRSTFIRE_URL = "https://api.appeks.com/aifitness-firebase/api/firestore/"
//    const val SECONDFIRE_URL = "https://api.appeks.com/aifitness-chatgpt/api/firestore/"
}

suspend fun postJson(
    url: String,
    json: JSONObject,
): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder().url(url).post(body).build()

    client.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IOException("Server returned ${response.code}")
        }
        responseBody
    }
}


fun getUid(context: Context): String? {
    val prefs = context.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
    return prefs.getString("uid", null)
}


object DataSyncManager {

    suspend fun fetchAndCacheData(
        context: Context,
        cacheKey: String,
        url: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("local_cache", Context.MODE_PRIVATE)

        val cachedData = prefs.getString(cacheKey, null)
        if (cachedData != null) {
            try {
                return@withContext JSONObject(cachedData)
            } catch (e: Exception) {
            }
        }

        val client = OkHttpClient()
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) return@withContext null

                val jsonResp = JSONObject(body)
                val document = jsonResp.optJSONObject("document") ?: return@withContext null

                prefs.edit().putString(cacheKey, document.toString()).apply()

                return@withContext document
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }
}