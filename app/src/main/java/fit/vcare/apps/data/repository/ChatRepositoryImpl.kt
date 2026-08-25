package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.data.mapper.*
import fit.vcare.apps.data.remote.ServerTimeSync
import fit.vcare.apps.domain.model.Conversation
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.ProposalStatus
import fit.vcare.apps.domain.model.RelationshipIndexEntry
import fit.vcare.apps.domain.model.RelationshipStatus
import fit.vcare.apps.domain.model.ReplyInfo
import fit.vcare.apps.domain.repository.ChatRepository
import fit.vcare.apps.room.ChatDatabase
import fit.vcare.apps.room.toEntity
import fit.vcare.apps.room.toMessage
import fit.vcare.apps.tools.FirestoreApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
//ChatRepositoryImpl
object ChatRepositoryImpl : ChatRepository {

    private const val TYPING_EXPIRY_MS = 6000L
    private const val MESSAGE_POLL_LIMIT = 50
    private const val INITIAL_PAGE_SIZE = 30
    private const val PAGE_SIZE = 30
    private val pollingJobs = ConcurrentHashMap<String, Job>()
    private val messageStates = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val visibleCounts = ConcurrentHashMap<String, Int>()
    private val typingJobs = ConcurrentHashMap<String, Job>()
    private val typingStates = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    private val partnerReadJobs = ConcurrentHashMap<String, Job>()
    private val partnerReadStates = ConcurrentHashMap<String, MutableStateFlow<Long?>>()
    private fun messageDao(context: Context) = ChatDatabase.getInstance(context).messageDao()
    private val deliveredNotified = ConcurrentHashMap<String, MutableSet<String>>() // conversationId -> messageId های چک‌شده این سشن

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
        messageId: String?, replyTo: ReplyInfo?
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
            status = MessageStatus.SENT,replyToMessageId = replyTo?.messageId,
            replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text,
            replyToType = replyTo?.type
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
        messageId: String?, replyTo: ReplyInfo?
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
            status = MessageStatus.SENT,replyToMessageId = replyTo?.messageId,
            replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text,
            replyToType = replyTo?.type
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
        messageId: String?, replyTo: ReplyInfo?
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
            fileSize = fileSize,
            replyToMessageId = replyTo?.messageId,
            replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text,
            replyToType = replyTo?.type
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

        messageDao(context).upsert(updated.toEntity())          // ← جدید: آپدیت فوری کش
        messageStates[conversationId]?.let { state ->
            state.value = state.value.map { if (it.messageId == messageId) updated else it }
        }
        syncConversationPreviewFromLatestMessage(context, conversationId)
        return Result.success(Unit)
    }

    override suspend fun deleteMessage(context: Context, conversationId: String, messageId: String): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        val ok = FirestoreApiClient.delete(context, path)
        if (!ok) return Result.failure(Exception("حذف پیام ناموفق بود"))

        messageDao(context).deleteMessage(conversationId, messageId) // ← جدید
        messageStates[conversationId]?.let { state ->
            state.value = state.value.filterNot { it.messageId == messageId }
        }
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
            messages.chunked(20).forEach { batch ->
                coroutineScope {
                    batch.map { msg ->
                        async { FirestoreApiClient.delete(context, "conversations/$conversationId/messages/${msg.messageId}") }
                    }.awaitAll()
                }
            }

            val currentConv = getConversation(context, conversationId).getOrNull()
            if (currentConv != null) {
                val cleared = currentConv.copy(lastMessage = null, lastMessageAt = null, lastMessageSenderId = null)
                FirestoreApiClient.write(context, "conversations/$conversationId", cleared.toJson())
            }

            messageDao(context).deleteAllForConversation(conversationId) // ← جدید
            visibleCounts[conversationId] = INITIAL_PAGE_SIZE
            messageStates[conversationId]?.value = emptyList()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteChatForMe(context: Context, conversationId: String, myUid: String): Result<Unit> {
        return try {
            // فقط کش محلی همین دستگاه پاک بشه — پیام‌های سرور دست‌نخورده بمونن،
            // چون بین من و پارتنرم مشترکن و نباید برای اون هم پاک بشه
            messageDao(context).deleteAllForConversation(conversationId)
            stopObservingMessages(conversationId)
            messageStates[conversationId]?.value = emptyList()
            visibleCounts.remove(conversationId)

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
            MessageType.VIDEO -> "🎥 ویدیو"
            MessageType.FILE -> "📎 ${message.fileName ?: "فایل"}"   // ← جدید
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
        incrementUnreadForRecipient(context, conversationId, senderId)
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

        toMark.forEach { alreadyHandled.add(it.messageId) } // اول مارک کن تا دوباره تلاش نشه

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
                        alreadyHandled.remove(msg.messageId) // اجازه بده دفعه بعد دوباره تلاش بشه
                    }
                }
            }.awaitAll()
        }
    }

    override fun observeMessages(
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
                        markMessagesDelivered(context, conversationId, viewerUid, window) // ← جدید
                    }
                }
                refreshStateFromRoom(context, conversationId, state)
                delay(intervalMs)
            }
        }
        pollingJobs[conversationId] = job
        return state.asStateFlow()
    }

     override fun stopObservingMessages(conversationId: String) {
        pollingJobs[conversationId]?.cancel()
        pollingJobs.remove(conversationId)
        deliveredNotified.remove(conversationId) // ← جدید، پاکسازی حافظه
    }
    override suspend fun loadOlderMessages(context: Context, conversationId: String): Result<Boolean> {
        return try {
            val currentLimit = visibleCounts[conversationId] ?: INITIAL_PAGE_SIZE
            val newLimit = currentLimit + PAGE_SIZE
            val localCount = messageDao(context).countForConversation(conversationId)

            // اگه Room کافی نداره، یه صفحه‌ی قدیمی‌تر از سرور بگیر و کش کن
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
    override suspend fun refreshNow(context: Context, conversationId: String) {
        val result = getRecentMessages(context, conversationId, MESSAGE_POLL_LIMIT)
        result.onSuccess { window ->
            if (window.isNotEmpty()) messageDao(context).upsertAll(window.map { it.toEntity() })
        }
        val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        refreshStateFromRoom(context, conversationId, state)
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
                        val isFresh = (ServerTimeSync.now()  - updatedAt) < TYPING_EXPIRY_MS
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
    private suspend fun incrementUnreadForRecipient(
        context: Context,
        conversationId: String,
        senderId: String
    ) {
        val conv = getConversation(context, conversationId).getOrNull() ?: return
        val recipientId = conv.participantIds.firstOrNull { it != senderId } ?: return
        FirestoreApiClient.increment(context, "conversations/$conversationId", "unread_$recipientId", 1.0)
        ensureRelationshipIndexEntry(context, conv.relationshipId, recipientId) // ← جدید
    }

    /** اگه ایندکس رابطه برای این کاربر (مثلاً چون چت رو حذف کرده بود) وجود نداشت، از روی relationship اصلی بازسازیش کن */
    private suspend fun ensureRelationshipIndexEntry(context: Context, relationshipId: String, uid: String) {
        val path = "users/$uid/partner_relationships/$relationshipId"
        val existing = FirestoreApiClient.read(context, path)
        val alreadyExists = existing != null && !existing.has("error") && existing.unwrapDocument().length() > 0
        if (alreadyExists) return

        val relRaw = FirestoreApiClient.read(context, "relationships/$relationshipId") ?: return
        if (relRaw.has("error")) return
        val relationship = relRaw.toRelationship(relationshipId)
        if (relationship.status != RelationshipStatus.ACTIVE) return

        val partnerUid = relationship.otherUid(uid)
        val now = FirestoreApiClient.getServerTimeMillis()
        val entry = RelationshipIndexEntry(relationshipId, partnerUid, relationship.status, relationship.createdAt, now)
        FirestoreApiClient.write(context, path, entry.toJson())
    }
    // در ChatRepositoryImpl
    override suspend fun resetUnreadCount(
        context: Context,
        conversationId: String,
        uid: String
    ): Result<Unit> {
        val current = getConversation(context, conversationId).getOrNull()
            ?: return Result.success(Unit)
        if ((current.unreadCounts[uid] ?: 0L) == 0L) return Result.success(Unit)

        val updated = current.copy(
            unreadCounts = current.unreadCounts.toMutableMap().apply { put(uid, 0L) }
        )
        val ok = FirestoreApiClient.write(context, "conversations/$conversationId", updated.toJson())
        return if (ok) Result.success(Unit) else Result.failure(Exception("بروزرسانی unread ناموفق بود"))
    }

    override suspend fun getRecentMessages(context: Context, conversationId: String, limit: Int): Result<List<Message>> {
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
    override suspend fun getMessagesBefore(
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

    private suspend fun refreshStateFromRoom(context: Context, conversationId: String, state: MutableStateFlow<List<Message>>) {
        val limit = visibleCounts[conversationId] ?: INITIAL_PAGE_SIZE
        val entities = messageDao(context).getRecent(conversationId, limit)
        state.value = entities.map { it.toMessage() }.sortedBy { it.createdAt }
    }
    override fun seedCachedMessages(conversationId: String, cachedMessages: List<Message>) {
        val state = messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        if (state.value.isEmpty() && cachedMessages.isNotEmpty()) {
            state.value = cachedMessages
        }
    }

    /** پیام‌های قدیمی‌تر از بازه‌ی تازه‌دریافتی دست‌نخورده می‌مونن؛ بازه‌ی جدید (که شامل ادیت/حذف‌های اخیره) جایگزین می‌شه */
    private fun mergeMessageWindow(cached: List<Message>, freshWindow: List<Message>): List<Message> {
        if (freshWindow.isEmpty()) return cached
        val windowStart = freshWindow.first().createdAt
        val untouchedOlder = cached.filter { it.createdAt < windowStart }
        return (untouchedOlder + freshWindow).sortedBy { it.createdAt }
    }
    override suspend fun toggleMessageReaction(
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
            newReactions.remove(uid)   // همون ری‌اکشن دوباره زده شد -> toggle off
        } else {
            newReactions[uid] = emoji  // ری‌اکشن جدید یا جایگزینی ری‌اکشن قبلی همین کاربر
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
    override suspend fun sendFileMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        caption: String,
        messageId: String?, replyTo: ReplyInfo?
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس فایل نامعتبر است"))

        val now = FirestoreApiClient.getServerTimeMillis()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId,
            conversationId = conversationId,
            senderId = senderId,
            text = caption.trim(),
            type = MessageType.FILE,
            mediaUrl = mediaUrl,
            createdAt = now,
            status = MessageStatus.SENT,
            mimeType = mimeType,
            fileSize = fileSize,
            fileName = fileName,replyToMessageId = replyTo?.messageId,
            replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text,
            replyToType = replyTo?.type
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال فایل ناموفق بود"))

        updateConversationPreview(context, conversationId, previewText = previewTextFor(message), senderId = senderId, at = now)
        return Result.success(message)
    }
    override suspend fun sendVideoMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        durationMs: Long,
        mimeType: String,
        fileSize: Long,
        caption: String,
        messageId: String?,
        replyTo: ReplyInfo?
    ): Result<Message> {
        if (mediaUrl.isBlank()) return Result.failure(IllegalArgumentException("آدرس ویدیو نامعتبر است"))

        val now = FirestoreApiClient.getServerTimeMillis()
        val newId = messageId ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = newId,
            conversationId = conversationId,
            senderId = senderId,
            text = caption.trim(),
            type = MessageType.VIDEO,
            mediaUrl = mediaUrl,
            createdAt = now,
            status = MessageStatus.SENT,
            durationMs = durationMs,
            mimeType = mimeType,
            fileSize = fileSize,
            replyToMessageId = replyTo?.messageId,
            replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text,
            replyToType = replyTo?.type
        )

        val ok = FirestoreApiClient.write(context, "conversations/$conversationId/messages/$newId", message.toJson())
        if (!ok) return Result.failure(Exception("ارسال ویدیو ناموفق بود"))

        updateConversationPreview(context, conversationId, previewText = previewTextFor(message), senderId = senderId, at = now)
        return Result.success(message)
    }
}