package fit.vcare.apps.data.remote

import android.content.Context
import fit.vcare.apps.data.repository.getMyEmailOrEmpty
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
 * از همان endpoint واقعی و از قبل موجود پروژه استفاده می‌کند (FoodRepository.uploadImageToServer).
 * هیچ endpoint جدیدی به بک‌اند اضافه نشده.
 *
 * POST https://api.appeks.com/aifitness-chatgpt/api/ai/upload-image
 * multipart fields: image (binary), email (string)
 * response: { "imageUrl": "..." }
 *
 * توجه: این endpoint بر خلاف FirestoreApiClient نیاز به Authorization ندارد و
 * شناسه‌اش email است (طبق رفتار موجود در FoodRepository) — عمداً همان رفتار حفظ شده.
 */
interface MediaRepository {
    suspend fun uploadImage(context: Context, imageBytes: ByteArray): Result<String>
}

object MediaRepositoryImpl : MediaRepository {

    private const val UPLOAD_URL = "https://api.appeks.com/aifitness-chatgpt/api/ai/upload-image"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun uploadImage(context: Context, imageBytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val email = getMyEmailOrEmpty(context)
                if (email.isBlank()) {
                    return@withContext Result.failure(Exception("لطفاً دوباره وارد شوید"))
                }

                val fileName = "chat_${System.currentTimeMillis()}.jpg"
                val imageBody = imageBytes.toRequestBody("image/*".toMediaType())

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", fileName, imageBody)
                    .addFormDataPart("email", email)
                    .build()

                val request = Request.Builder()
                    .url(UPLOAD_URL)
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("آپلود تصویر ناموفق بود (${response.code})"))
                    }

                    val json = JSONObject(bodyString)
                    val imageUrl = json.optString("imageUrl")
                    if (imageUrl.isBlank()) {
                        return@withContext Result.failure(Exception("سرور آدرس تصویر را برنگرداند"))
                    }

                    Result.success(imageUrl)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}