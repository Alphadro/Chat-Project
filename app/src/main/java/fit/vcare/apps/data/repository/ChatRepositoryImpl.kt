package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.data.mapper.*
import fit.vcare.apps.domain.model.Conversation
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.ProposalStatus
import fit.vcare.apps.domain.repository.ChatRepository
import fit.vcare.apps.tools.FirestoreApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ChatRepositoryImpl : ChatRepository {

    private const val TYPING_EXPIRY_MS = 6000L

    private val pollingJobs = ConcurrentHashMap<String, Job>()
    private val messageStates = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()

    private val typingJobs = ConcurrentHashMap<String, Job>()
    private val typingStates = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    private val partnerReadJobs = ConcurrentHashMap<String, Job>()
    private val partnerReadStates = ConcurrentHashMap<String, MutableStateFlow<Long?>>()

    override suspend fun getOrCreateConversation(
        context: Context,
        relationshipId: String,
        participantIds: List<String>
    ): Result<Conversation> {
        val existingRaw = FirestoreApiClient.read(context, "conversations/$relationshipId")
        if (existingRaw != null && !existingRaw.has("error") && existingRaw.unwrapDocument().length() > 0) {
            return Result.success(existingRaw.toConversation(relationshipId))
        }

        val now = FirestoreApiClient.getServerTimeMillis()
        val conversation = Conversation(
            conversationId = relationshipId,
            relationshipId = relationshipId,
            participantIds = participantIds,
            createdAt = now
        )
        val ok = FirestoreApiClient.write(context, "conversations/$relationshipId", conversation.toJson())
        return if (ok) Result.success(conversation)
        else Result.failure(Exception("خطا در ایجاد Conversation"))
    }

    override suspend fun getConversation(context: Context, conversationId: String): Result<Conversation?> {
        val raw = FirestoreApiClient.read(context, "conversations/$conversationId")
        if (raw == null || raw.has("error")) return Result.success(null)
        val doc = raw.unwrapDocument()
        if (doc.length() == 0) return Result.success(null)
        return Result.success(raw.toConversation(conversationId))
    }

    override suspend fun sendMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        text: String,
        messageId: String?
    ): Result<Message> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("پیام خالی است"))

        val now = FirestoreApiClient.getServerTimeMillis()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId,
            conversationId = conversationId,
            senderId = senderId,
            text = text.trim(),
            type = MessageType.TEXT,
            mediaUrl = null,
            createdAt = now,
            status = MessageStatus.SENT
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال پیام ناموفق بود"))

        updateConversationPreview(context, conversationId, previewText = previewTextFor(message), senderId = senderId, at = now)

        return Result.success(message)
    }

    override suspend fun sendImageMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        caption: String,
        messageId: String?
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس تصویر نامعتبر است"))

        val now = FirestoreApiClient.getServerTimeMillis()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId,
            conversationId = conversationId,
            senderId = senderId,
            text = caption.trim(),
            type = MessageType.IMAGE,
            mediaUrl = mediaUrl,
            createdAt = now,
            status = MessageStatus.SENT
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال تصویر ناموفق بود"))

        updateConversationPreview(context, conversationId, previewText = previewTextFor(message), senderId = senderId, at = now)

        return Result.success(message)
    }

    override suspend fun sendAudioMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        durationMs: Long,
        mimeType: String,
        fileSize: Long,
        caption: String,
        messageId: String?
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس فایل صوتی نامعتبر است"))

        val now = FirestoreApiClient.getServerTimeMillis()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId,
            conversationId = conversationId,
            senderId = senderId,
            text = caption.trim(),
            type = MessageType.AUDIO,
            mediaUrl = mediaUrl,
            createdAt = now,
            status = MessageStatus.SENT,
            durationMs = durationMs,
            mimeType = mimeType,
            fileSize = fileSize
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال پیام صوتی ناموفق بود"))

        updateConversationPreview(context, conversationId, previewText = previewTextFor(message), senderId = senderId, at = now)

        return Result.success(message)
    }

    override suspend fun editMessage(
        context: Context,
        conversationId: String,
        messageId: String,
        newText: String
    ): Result<Unit> {
        if (messageId.isBlank()) return Result.failure(Exception("شناسه پیام نامعتبر است"))

        val path = "conversations/$conversationId/messages/$messageId"
        val existingRaw = FirestoreApiClient.read(context, path)
            ?: return Result.failure(Exception("پیام یافت نشد"))
        val existingDoc = existingRaw.unwrapDocument()
        if (existingDoc.length() == 0) return Result.failure(Exception("پیام یافت نشد"))

        val existing = existingRaw.toMessage(fallbackId = messageId, conversationId = conversationId)
        val updated = existing.copy(text = newText.trim(), isEdited = true)

        val ok = FirestoreApiClient.write(context, path, updated.toJson())
        if (!ok) return Result.failure(Exception("ویرایش پیام ناموفق بود"))

        syncConversationPreviewFromLatestMessage(context, conversationId)

        return Result.success(Unit)
    }

    override suspend fun deleteMessage(
        context: Context,
        conversationId: String,
        messageId: String
    ): Result<Unit> {
        if (messageId.isBlank()) return Result.failure(Exception("شناسه پیام نامعتبر است"))

        val path = "conversations/$conversationId/messages/$messageId"
        val ok = FirestoreApiClient.delete(context, path)
        if (!ok) return Result.failure(Exception("حذف پیام ناموفق بود"))

        syncConversationPreviewFromLatestMessage(context, conversationId)

        return Result.success(Unit)
    }

    override suspend fun sendWallpaperProposal(
        context: Context,
        conversationId: String,
        senderId: String,
        backgroundUrl: String,
        messageId: String?
    ): Result<Message> {
        if (backgroundUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس پس‌زمینه نامعتبر است"))

        val now = FirestoreApiClient.getServerTimeMillis()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId,
            conversationId = conversationId,
            senderId = senderId,
            text = "",
            type = MessageType.WALLPAPER_PROPOSAL,
            mediaUrl = backgroundUrl,
            createdAt = now,
            status = MessageStatus.SENT,
            proposalStatus = ProposalStatus.PENDING
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال پیشنهاد پس‌زمینه ناموفق بود"))

        updateConversationPreview(context, conversationId, previewText = previewTextFor(message), senderId = senderId, at = now)
        return Result.success(message)
    }

    override suspend fun updateWallpaperProposalStatus(
        context: Context,
        conversationId: String,
        messageId: String,
        status: ProposalStatus
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        val existingRaw = FirestoreApiClient.read(context, path)
            ?: return Result.failure(Exception("پیام یافت نشد"))
        val existingDoc = existingRaw.unwrapDocument()
        if (existingDoc.length() == 0) return Result.failure(Exception("پیام یافت نشد"))

        val existing = existingRaw.toMessage(fallbackId = messageId, conversationId = conversationId)
        val updated = existing.copy(proposalStatus = status)

        val ok = FirestoreApiClient.write(context, path, updated.toJson())
        return if (ok) Result.success(Unit) else Result.failure(Exception("بروزرسانی وضعیت پیشنهاد ناموفق بود"))
    }
    override suspend fun clearChatHistory(context: Context, conversationId: String): Result<Unit> {
        return try {
            val messages = getMessages(context, conversationId).getOrDefault(emptyList())
            messages.forEach { msg ->
                FirestoreApiClient.delete(context, "conversations/$conversationId/messages/${msg.messageId}")
            }

            // پیش‌نمایش آخرین پیام توی لیست چت‌ها هم پاک بشه
            val currentConv = getConversation(context, conversationId).getOrNull()
            if (currentConv != null) {
                val cleared = currentConv.copy(lastMessage = null, lastMessageAt = null, lastMessageSenderId = null)
                FirestoreApiClient.write(context, "conversations/$conversationId", cleared.toJson())
            }

            messageStates[conversationId]?.value = emptyList()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteChatForMe(context: Context, conversationId: String, myUid: String): Result<Unit> {
        return try {
            clearChatHistory(context, conversationId)
            val ok = FirestoreApiClient.delete(context, "users/$myUid/partner_relationships/$conversationId")
            if (ok) Result.success(Unit) else Result.failure(Exception("حذف چت ناموفق بود"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // در previewTextFor موجود، یک case اضافه کن:
    private fun previewTextFor(message: Message): String {
        return when (message.type) {
            MessageType.IMAGE -> if (message.text.isNotBlank()) "📷 ${message.text}" else "📷 عکس"
            MessageType.AUDIO -> "🎤 پیام صوتی"
            MessageType.WALLPAPER_PROPOSAL -> "🖼️ پیشنهاد پس‌زمینه چت"
            else -> message.text
        }
    }

    private suspend fun syncConversationPreviewFromLatestMessage(context: Context, conversationId: String) {
        val messagesResult = getMessages(context, conversationId)
        val messages = messagesResult.getOrNull() ?: return

        val currentConv = getConversation(context, conversationId).getOrNull() ?: return

        val latest = messages.maxByOrNull { it.createdAt }
        val updated = if (latest != null) {
            currentConv.copy(
                lastMessage = previewTextFor(latest),
                lastMessageAt = latest.createdAt,
                lastMessageSenderId = latest.senderId
            )
        } else {
            currentConv.copy(lastMessage = null, lastMessageAt = null, lastMessageSenderId = null)
        }

        FirestoreApiClient.write(context, "conversations/$conversationId", updated.toJson())
    }

    private suspend fun updateConversationPreview(
        context: Context,
        conversationId: String,
        previewText: String,
        senderId: String,
        at: Long
    ) {
        val currentConv = getConversation(context, conversationId).getOrNull() ?: return
        val updated = currentConv.copy(
            lastMessage = previewText,
            lastMessageAt = at,
            lastMessageSenderId = senderId
        )
        FirestoreApiClient.write(context, "conversations/$conversationId", updated.toJson())
    }

    override suspend fun getMessages(context: Context, conversationId: String): Result<List<Message>> {
        return try {
            val docs = FirestoreApiClient.list(context, "conversations/$conversationId/messages")
            val messages = docs.map { it.toMessage(fallbackId = "", conversationId = conversationId) }
                .sortedBy { it.createdAt }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeMessages(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        intervalMs: Long
    ): StateFlow<List<Message>> {
        val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

        pollingJobs[conversationId]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val result = getMessages(context, conversationId)
                result.onSuccess { messages ->
                    state.value = messages
                }
                delay(intervalMs)
            }
        }
        pollingJobs[conversationId] = job
        return state.asStateFlow()
    }

    override suspend fun refreshNow(context: Context, conversationId: String) {
        val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        val result = getMessages(context, conversationId)
        result.onSuccess { messages -> state.value = messages }
    }

    override fun stopObservingMessages(conversationId: String) {
        pollingJobs[conversationId]?.cancel()
        pollingJobs.remove(conversationId)
    }

    override suspend fun updateLastRead(
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

    override suspend fun setTypingState(
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

    override fun observePartnerTyping(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        partnerUid: String,
        intervalMs: Long
    ): StateFlow<Boolean> {
        val state = typingStates.getOrPut(conversationId) { MutableStateFlow(false) }
        typingJobs[conversationId]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val raw = FirestoreApiClient.read(context, "conversations/$conversationId/typing/$partnerUid")
                if (raw != null && !raw.has("error")) {
                    val doc = raw.unwrapDocument()
                    if (doc.length() > 0) {
                        val isTyping = doc.optBoolean("isTyping", false)
                        val updatedAt = doc.optLong("updatedAt", 0L)
                        val isFresh = (System.currentTimeMillis() - updatedAt) < TYPING_EXPIRY_MS
                        state.value = isTyping && isFresh
                    }
                }
                delay(intervalMs)
            }
        }
        typingJobs[conversationId] = job
        return state.asStateFlow()
    }

    override fun stopObservingTyping(conversationId: String) {
        typingJobs[conversationId]?.cancel()
        typingJobs.remove(conversationId)
    }

    override fun observePartnerReadState(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        partnerUid: String,
        intervalMs: Long
    ): StateFlow<Long?> {
        val state = partnerReadStates.getOrPut(conversationId) { MutableStateFlow(null) }
        partnerReadJobs[conversationId]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val raw = FirestoreApiClient.read(context, "conversations/$conversationId/reads/$partnerUid")
                if (raw != null && !raw.has("error")) {
                    val doc = raw.unwrapDocument()
                    if (doc.length() > 0) {
                        state.value = doc.optLong("lastReadAt", 0L)
                    }
                }
                delay(intervalMs)
            }
        }
        partnerReadJobs[conversationId] = job
        return state.asStateFlow()
    }

    override fun stopObservingPartnerReadState(conversationId: String) {
        partnerReadJobs[conversationId]?.cancel()
        partnerReadJobs.remove(conversationId)
    }
}