package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.data.remote.ServerTimeSync
import fit.vcare.apps.tools.FirestoreApiClient
import fit.vcare.apps.tools.FirestorePoller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

//PresenceTypingRepositoryImpl.kt
object PresenceTypingRepositoryImpl {

    private const val TYPING_EXPIRY_MS = 6000L

    private val typingJobs = ConcurrentHashMap<String, Job>()
    private val typingStates = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val partnerReadJobs = ConcurrentHashMap<String, Job>()
    private val partnerReadStates = ConcurrentHashMap<String, MutableStateFlow<Long?>>()

    suspend fun updateLastRead(
        context: Context,
        conversationId: String,
        uid: String,
        lastReadAt: Long
    ): Result<Unit> {
        val data = JSONObject().apply {
            put("uid", uid)
            put("lastReadAt", lastReadAt)
        }
        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/reads/$uid", data)
        return if (ok) Result.success(Unit) else Result.failure(Exception("بروزرسانی read state ناموفق بود"))
    }

    suspend fun setTypingState(
        context: Context,
        conversationId: String,
        uid: String,
        isTyping: Boolean
    ): Result<Unit> {
        val now = FirestoreApiClient.getServerTimeMillis()
        val data = JSONObject().apply {
            put("uid", uid)
            put("isTyping", isTyping)
            put("updatedAt", now)
        }
        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/typing/$uid", data)
        return if (ok) Result.success(Unit) else Result.failure(Exception("بروزرسانی وضعیت تایپ ناموفق بود"))
    }

    fun observePartnerTyping(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        partnerUid: String,
        intervalMs: Long
    ): StateFlow<Boolean> = FirestorePoller.observe(
        scope = scope,
        jobsMap = typingJobs,
        statesMap = typingStates,
        key = conversationId,
        context = context,
        path = "conversations/$conversationId/typing/$partnerUid",
        intervalMs = intervalMs,
        initialValue = false
    ) { doc ->
        val isTyping = doc.optBoolean("isTyping", false)
        val updatedAt = doc.optLong("updatedAt", 0L)
        val isFresh = (ServerTimeSync.now() - updatedAt) < TYPING_EXPIRY_MS
        isTyping && isFresh
    }

    fun stopObservingTyping(conversationId: String) =
        FirestorePoller.stop(typingJobs, conversationId)

    fun observePartnerReadState(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        partnerUid: String,
        intervalMs: Long
    ): StateFlow<Long?> = FirestorePoller.observe(
        scope = scope,
        jobsMap = partnerReadJobs,
        statesMap = partnerReadStates,
        key = conversationId,
        context = context,
        path = "conversations/$conversationId/reads/$partnerUid",
        intervalMs = intervalMs,
        initialValue = null
    ) { doc -> doc.optLong("lastReadAt", 0L) }

    fun stopObservingPartnerReadState(conversationId: String) =
        FirestorePoller.stop(partnerReadJobs, conversationId)
}