package fit.vcare.apps.data.remote

import android.content.Context
import android.util.Log
import fit.vcare.apps.data.repository.getMyTokenOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * آپلود تصویر و صدای چت. هر دو تابع عمومی (uploadImage, uploadAudio) بدون تغییر رفتار
 * روی یک تابع خصوصی مشترک (uploadFile) سوار شدند — رفتار uploadImage عیناً حفظ شده،
 * فقط منطق تکراری حذف شد.
 */
interface MediaRepository {
    suspend fun uploadImage(context: Context, imageBytes: ByteArray): Result<String>
    suspend fun uploadAudio(context: Context, audioBytes: ByteArray, mimeType: String): Result<String>
}

object MediaRepositoryImpl : MediaRepository {

    private const val UPLOAD_IMAGE_URL = "https://api.appeks.com/aifitness-firebase/api/chat/upload-image"
    private const val UPLOAD_AUDIO_URL = "https://api.appeks.com/aifitness-firebase/api/chat/upload-audio"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun uploadImage(context: Context, imageBytes: ByteArray): Result<String> {
        val fileName = "chat_${System.currentTimeMillis()}.jpg"
        return uploadFile(
            context = context,
            url = UPLOAD_IMAGE_URL,
            fieldName = "image",
            fileName = fileName,
            bytes = imageBytes,
            mimeType = "image/jpeg",
            responseUrlKey = "imageUrl"
        )
    }

    override suspend fun uploadAudio(context: Context, audioBytes: ByteArray, mimeType: String): Result<String> {
        val extension = when {
            mimeType.contains("mp4") -> "m4a"
            mimeType.contains("aac") -> "aac"
            else -> "m4a"
        }
        val fileName = "voice_${System.currentTimeMillis()}.$extension"
        return uploadFile(
            context = context,
            url = UPLOAD_AUDIO_URL,
            fieldName = "audio",
            fileName = fileName,
            bytes = audioBytes,
            mimeType = mimeType,
            responseUrlKey = "audioUrl"
        )
    }

    private suspend fun uploadFile(
        context: Context,
        url: String,
        fieldName: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        responseUrlKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getMyTokenOrEmpty(context)
            Log.d("MediaUpload", "url=$url tokenBlank=${token.isBlank()} tokenLen=${token.length}")

            if (token.isBlank()) {
                return@withContext Result.failure(Exception("لطفاً دوباره وارد شوید"))
            }

            val fileBody = bytes.toRequestBody(mimeType.toMediaType())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(fieldName, fileName, fileBody)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                // این لاگ رو همیشه بزن، چه موفق چه ناموفق
                Log.d("MediaUpload", "code=${response.code} body=$bodyString")

                if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("آپلود ناموفق بود (${response.code}): $bodyString"))
                }

                val json = JSONObject(bodyString)
                val fileUrl = json.optString(responseUrlKey)
                if (fileUrl.isBlank()) {
                    return@withContext Result.failure(Exception("سرور آدرس فایل را برنگرداند"))
                }

                Result.success(fileUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}