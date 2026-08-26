package fit.vcare.apps.tools

import android.content.Context
import fit.vcare.apps.data.mapper.unwrapDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

//FirestorePoller.kt
object FirestorePoller {

    /**
     * یک observe عمومی برای هر جریانی که با polling روی یک path از Firestore کار می‌کنه.
     * جایگزین ۴ حلقه‌ی تکراری در ChatRepositoryImpl و PartnerRepositoryImpl می‌شه.
     *
     * توجه: jobsMap و statesMap رو خود caller پاس می‌ده (هر repository نقشه‌های خودش رو نگه می‌داره)
     * چون کلیدها (مثلاً conversationId) بین typing/readState/presence/relationship مشترکن
     * و باید در نقشه‌های جدا از هم بمونن.
     */
    fun <T> observe(
        scope: CoroutineScope,
        jobsMap: ConcurrentHashMap<String, Job>,
        statesMap: ConcurrentHashMap<String, MutableStateFlow<T>>,
        key: String,
        context: Context,
        path: String,
        intervalMs: Long,
        initialValue: T,
        parser: (doc: JSONObject) -> T
    ): StateFlow<T> {
        val state = statesMap.getOrPut(key) { MutableStateFlow(initialValue) }
        jobsMap[key]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val raw = FirestoreApiClient.read(context, path)
                if (raw != null && !raw.has("error")) {
                    val doc = raw.unwrapDocument()
                    if (doc.length() > 0) {
                        state.value = parser(doc)
                    }
                }
                delay(intervalMs)
            }
        }
        jobsMap[key] = job
        return state.asStateFlow()
    }

    fun stop(jobsMap: ConcurrentHashMap<String, Job>, key: String) {
        jobsMap[key]?.cancel()
        jobsMap.remove(key)
    }
}