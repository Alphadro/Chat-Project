package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.data.mapper.toJson
import fit.vcare.apps.data.mapper.toMessage
import fit.vcare.apps.data.mapper.unwrapDocument
import fit.vcare.apps.data.remote.ServerTimeSync
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.ProposalStatus
import fit.vcare.apps.domain.model.ReplyInfo
import fit.vcare.apps.room.ChatDatabase
import fit.vcare.apps.room.toEntity
import fit.vcare.apps.room.toMessage
import fit.vcare.apps.tools.FirestoreApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

//MessageRepositoryImpl.kt
object MessageRepositoryImpl {

    private const val MESSAGE_POLL_LIMIT = 50
    private const val INITIAL_PAGE_SIZE = 30
    private const val PAGE_SIZE = 30

    private val pollingJobs = ConcurrentHashMap<String, Job>()
    private val messageStates = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val visibleCounts = ConcurrentHashMap<String, Int>()
    private val deliveredNotified = ConcurrentHashMap<String, MutableSet<String>>()

    private fun messageDao(context: Context) = ChatDatabase.getInstance(context).messageDao()

    suspend fun sendMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        text: String,
        messageId: String? = null,
        replyTo: ReplyInfo? = null
    ): Result<Message> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("پیام خالی است"))

        val now = ServerTimeSync.now()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId, conversationId = conversationId, senderId = senderId,
            text = text.trim(), type = MessageType.TEXT, mediaUrl = null,
            createdAt = now, status = MessageStatus.SENT,
            replyToMessageId = replyTo?.messageId, replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text, replyToType = replyTo?.type
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال پیام ناموفق بود"))

        ConversationRepositoryImpl.updateConversationPreview(context, conversationId, previewTextFor(message), senderId, now)
        return Result.success(message)
    }

    suspend fun sendImageMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        caption: String = "",
        messageId: String? = null,
        replyTo: ReplyInfo? = null
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس تصویر نامعتبر است"))

        val now = ServerTimeSync.now()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId, conversationId = conversationId, senderId = senderId,
            text = caption.trim(), type = MessageType.IMAGE, mediaUrl = mediaUrl,
            createdAt = now, status = MessageStatus.SENT,
            replyToMessageId = replyTo?.messageId, replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text, replyToType = replyTo?.type
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال تصویر ناموفق بود"))

        ConversationRepositoryImpl.updateConversationPreview(context, conversationId, previewTextFor(message), senderId, now)
        return Result.success(message)
    }

    suspend fun sendAudioMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        durationMs: Long,
        mimeType: String,
        fileSize: Long,
        caption: String = "",
        messageId: String? = null,
        replyTo: ReplyInfo? = null
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس فایل صوتی نامعتبر است"))

        val now = ServerTimeSync.now()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId, conversationId = conversationId, senderId = senderId,
            text = caption.trim(), type = MessageType.AUDIO, mediaUrl = mediaUrl,
            createdAt = now, status = MessageStatus.SENT,
            durationMs = durationMs, mimeType = mimeType, fileSize = fileSize,
            replyToMessageId = replyTo?.messageId, replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text, replyToType = replyTo?.type
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال پیام صوتی ناموفق بود"))

        ConversationRepositoryImpl.updateConversationPreview(context, conversationId, previewTextFor(message), senderId, now)
        return Result.success(message)
    }

    suspend fun sendFileMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        caption: String = "",
        messageId: String? = null,
        replyTo: ReplyInfo? = null
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس فایل نامعتبر است"))

        val now = ServerTimeSync.now()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId, conversationId = conversationId, senderId = senderId,
            text = caption.trim(), type = MessageType.FILE, mediaUrl = mediaUrl,
            createdAt = now, status = MessageStatus.SENT,
            mimeType = mimeType, fileSize = fileSize, fileName = fileName,
            replyToMessageId = replyTo?.messageId, replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text, replyToType = replyTo?.type
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال فایل ناموفق بود"))

        ConversationRepositoryImpl.updateConversationPreview(context, conversationId, previewTextFor(message), senderId, now)
        return Result.success(message)
    }

    suspend fun sendVideoMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        durationMs: Long,
        mimeType: String,
        fileSize: Long,
        caption: String = "",
        messageId: String? = null,
        replyTo: ReplyInfo? = null
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس ویدیو نامعتبر است"))

        val now = ServerTimeSync.now()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId, conversationId = conversationId, senderId = senderId,
            text = caption.trim(), type = MessageType.VIDEO, mediaUrl = mediaUrl,
            createdAt = now, status = MessageStatus.SENT,
            durationMs = durationMs, mimeType = mimeType, fileSize = fileSize,
            replyToMessageId = replyTo?.messageId, replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text, replyToType = replyTo?.type
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال ویدیو ناموفق بود"))

        ConversationRepositoryImpl.updateConversationPreview(context, conversationId, previewTextFor(message), senderId, now)
        return Result.success(message)
    }

    suspend fun editMessage(
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

        messageDao(context).upsert(updated.toEntity())
        messageStates[conversationId]?.let { state ->
            state.value = state.value.map { if (it.messageId == messageId) updated else it }
        }
        ConversationRepositoryImpl.syncConversationPreviewFromLatestMessage(context, conversationId)
        return Result.success(Unit)
    }

    suspend fun deleteMessage(context: Context, conversationId: String, messageId: String): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        val ok = FirestoreApiClient.delete(context, path)
        if (!ok) return Result.failure(Exception("حذف پیام ناموفق بود"))

        messageDao(context).deleteMessage(conversationId, messageId)
        messageStates[conversationId]?.let { state ->
            state.value = state.value.filterNot { it.messageId == messageId }
        }
        ConversationRepositoryImpl.syncConversationPreviewFromLatestMessage(context, conversationId)
        return Result.success(Unit)
    }

    suspend fun sendWallpaperProposal(
        context: Context,
        conversationId: String,
        senderId: String,
        backgroundUrl: String,
        messageId: String? = null
    ): Result<Message> {
        if (backgroundUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس پس‌زمینه نامعتبر است"))

        val now = ServerTimeSync.now()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId, conversationId = conversationId, senderId = senderId,
            text = "", type = MessageType.WALLPAPER_PROPOSAL, mediaUrl = backgroundUrl,
            createdAt = now, status = MessageStatus.SENT, proposalStatus = ProposalStatus.PENDING
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال پیشنهاد پس‌زمینه ناموفق بود"))

        ConversationRepositoryImpl.updateConversationPreview(context, conversationId, previewTextFor(message), senderId, now)
        return Result.success(message)
    }

    suspend fun updateWallpaperProposalStatus(
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

    suspend fun getMessages(context: Context, conversationId: String): Result<List<Message>> {
        return try {
            val docs = FirestoreApiClient.list(context, "conversations/$conversationId/messages")
            val messages = docs.map { it.toMessage(fallbackId = "", conversationId = conversationId) }
                .sortedBy { it.createdAt }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentMessages(context: Context, conversationId: String, limit: Int): Result<List<Message>> {
        return try {
            val docs = FirestoreApiClient.list(
                context, "conversations/$conversationId/messages",
                orderBy = "createdAt", orderDesc = true, limit = limit
            )
            val messages = docs.map { it.toMessage(fallbackId = "", conversationId = conversationId) }
                .sortedBy { it.createdAt }
            Result.success(messages)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getMessagesBefore(
        context: Context, conversationId: String, beforeTimestamp: Long, limit: Int
    ): Result<List<Message>> {
        return try {
            val docs = FirestoreApiClient.list(
                context, "conversations/$conversationId/messages",
                orderBy = "createdAt", orderDesc = true, limit = limit,
                whereField = "createdAt", whereOp = "<", whereValue = beforeTimestamp
            )
            val messages = docs.map { it.toMessage(fallbackId = "", conversationId = conversationId) }
                .sortedBy { it.createdAt }
            Result.success(messages)
        } catch (e: Exception) { Result.failure(e) }
    }

    private suspend fun markMessagesDelivered(
        context: Context,
        conversationId: String,
        viewerUid: String,
        messages: List<Message>
    ) {
        val alreadyHandled = deliveredNotified.getOrPut(conversationId) {
            java.util.Collections.synchronizedSet(mutableSetOf())
        }
        val toMark = messages.filter {
            it.senderId != viewerUid && it.status == MessageStatus.SENT && it.messageId !in alreadyHandled
        }
        if (toMark.isEmpty()) return

        toMark.forEach { alreadyHandled.add(it.messageId) }

        coroutineScope {
            toMark.map { msg ->
                async {
                    val path = "conversations/$conversationId/messages/${msg.messageId}"
                    val updated = msg.copy(status = MessageStatus.DELIVERED)
                    val ok = FirestoreApiClient.write(context, path, updated.toJson())
                    if (ok) {
                        messageDao(context).upsert(updated.toEntity())
                        messageStates[conversationId]?.let { state ->
                            state.value = state.value.map { if (it.messageId == msg.messageId) updated else it }
                        }
                    } else {
                        alreadyHandled.remove(msg.messageId)
                    }
                }
            }.awaitAll()
        }
    }

    fun observeMessages(
        scope: CoroutineScope, context: Context, conversationId: String, viewerUid: String, intervalMs: Long
    ): StateFlow<List<Message>> {
        val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        visibleCounts[conversationId] = INITIAL_PAGE_SIZE
        pollingJobs[conversationId]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            refreshStateFromRoom(context, conversationId, state)

            while (isActive) {
                val result = getRecentMessages(context, conversationId, MESSAGE_POLL_LIMIT)
                result.onSuccess { window ->
                    if (window.isNotEmpty()) {
                        messageDao(context).upsertAll(window.map { it.toEntity() })
                        markMessagesDelivered(context, conversationId, viewerUid, window)
                    }
                }
                refreshStateFromRoom(context, conversationId, state)
                delay(intervalMs)
            }
        }
        pollingJobs[conversationId] = job
        return state.asStateFlow()
    }

    fun stopObservingMessages(conversationId: String) {
        pollingJobs[conversationId]?.cancel()
        pollingJobs.remove(conversationId)
        deliveredNotified.remove(conversationId)
    }

    suspend fun loadOlderMessages(context: Context, conversationId: String): Result<Boolean> {
        return try {
            val currentLimit = visibleCounts[conversationId] ?: INITIAL_PAGE_SIZE
            val newLimit = currentLimit + PAGE_SIZE
            val localCount = messageDao(context).countForConversation(conversationId)

            if (localCount < newLimit) {
                val oldestLocal = messageDao(context).getOldestTimestamp(conversationId) ?: Long.MAX_VALUE
                val older = getMessagesBefore(context, conversationId, oldestLocal, PAGE_SIZE).getOrDefault(emptyList())
                if (older.isNotEmpty()) {
                    messageDao(context).upsertAll(older.map { it.toEntity() })
                }
            }

            visibleCounts[conversationId] = newLimit
            val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
            refreshStateFromRoom(context, conversationId, state)

            val totalNow = messageDao(context).countForConversation(conversationId)
            Result.success(totalNow > newLimit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun refreshNow(context: Context, conversationId: String) {
        val result = getRecentMessages(context, conversationId, MESSAGE_POLL_LIMIT)
        result.onSuccess { window ->
            if (window.isNotEmpty()) messageDao(context).upsertAll(window.map { it.toEntity() })
        }
        val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        refreshStateFromRoom(context, conversationId, state)
    }

    private suspend fun refreshStateFromRoom(context: Context, conversationId: String, state: MutableStateFlow<List<Message>>) {
        val limit = visibleCounts[conversationId] ?: INITIAL_PAGE_SIZE
        val entities = messageDao(context).getRecent(conversationId, limit)
        state.value = entities.map { it.toMessage() }.sortedBy { it.createdAt }
    }

    fun seedCachedMessages(conversationId: String, cachedMessages: List<Message>) {
        val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        if (state.value.isEmpty() && cachedMessages.isNotEmpty()) {
            state.value = cachedMessages
        }
    }

    suspend fun toggleMessageReaction(
        context: Context,
        conversationId: String,
        messageId: String,
        uid: String,
        emoji: String
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        val existingRaw = FirestoreApiClient.read(context, path)
            ?: return Result.failure(Exception("پیام یافت نشد"))
        val existingDoc = existingRaw.unwrapDocument()
        if (existingDoc.length() == 0) return Result.failure(Exception("پیام یافت نشد"))

        val existing = existingRaw.toMessage(fallbackId = messageId, conversationId = conversationId)
        val newReactions = existing.reactions.toMutableMap()
        if (newReactions[uid] == emoji) {
            newReactions.remove(uid)
        } else {
            newReactions[uid] = emoji
        }
        val updated = existing.copy(reactions = newReactions)

        val ok = FirestoreApiClient.write(context, path, updated.toJson())
        if (!ok) return Result.failure(Exception("ثبت ری‌اکشن ناموفق بود"))

        messageDao(context).upsert(updated.toEntity())
        messageStates[conversationId]?.let { state ->
            state.value = state.value.map { if (it.messageId == messageId) updated else it }
        }
        return Result.success(Unit)
    }

    // ── هلپرهای internal که فقط ConversationRepositoryImpl صداشون می‌زنه ──
    internal suspend fun clearLocalCacheKeepPaging(context: Context, conversationId: String) {
        messageDao(context).deleteAllForConversation(conversationId)
        visibleCounts[conversationId] = INITIAL_PAGE_SIZE
        messageStates[conversationId]?.value = emptyList()
    }

    internal suspend fun clearLocalCacheAndForget(context: Context, conversationId: String) {
        messageDao(context).deleteAllForConversation(conversationId)
        messageStates[conversationId]?.value = emptyList()
        visibleCounts.remove(conversationId)
    }
}