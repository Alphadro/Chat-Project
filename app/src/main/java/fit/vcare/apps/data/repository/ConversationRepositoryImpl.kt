package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.data.mapper.toConversation
import fit.vcare.apps.data.mapper.toJson
import fit.vcare.apps.data.mapper.toRelationship
import fit.vcare.apps.data.mapper.unwrapDocument
import fit.vcare.apps.data.remote.ServerTimeSync
import fit.vcare.apps.domain.model.Conversation
import fit.vcare.apps.domain.model.RelationshipIndexEntry
import fit.vcare.apps.domain.model.RelationshipStatus
import fit.vcare.apps.tools.FirestoreApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

//ConversationRepositoryImpl.kt
object ConversationRepositoryImpl {
    private val verifiedRelationshipIndexes = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun getOrCreateConversation(
        context: Context,
        relationshipId: String,
        participantIds: List<String>
    ): Result<Conversation> {
        val existingRaw = FirestoreApiClient.read(context, "conversations/$relationshipId")
        if (existingRaw != null && !existingRaw.has("error") && existingRaw.unwrapDocument().length() > 0) {
            return Result.success(existingRaw.toConversation(relationshipId))
        }

        val now = ServerTimeSync.now()
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

    suspend fun getConversation(context: Context, conversationId: String): Result<Conversation?> {
        val raw = FirestoreApiClient.read(context, "conversations/$conversationId")
        if (raw == null || raw.has("error")) return Result.success(null)
        val doc = raw.unwrapDocument()
        if (doc.length() == 0) return Result.success(null)
        return Result.success(raw.toConversation(conversationId))
    }

    /** صدا زده می‌شه از MessageRepositoryImpl بعد از ارسال موفق هر نوع پیام */
    suspend fun updateConversationPreview(
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
        incrementUnreadForRecipient(context, updated, senderId)
    }


    private suspend fun incrementUnreadForRecipient(
        context: Context,
        conv: Conversation,
        senderId: String
    ) {
        val recipientId = conv.participantIds.firstOrNull { it != senderId } ?: return
        FirestoreApiClient.increment(context, "conversations/${conv.conversationId}", "unread_$recipientId", 1.0)

        if (conv.relationshipId !in verifiedRelationshipIndexes) {
            ensureRelationshipIndexEntry(context, conv.relationshipId, recipientId)
            verifiedRelationshipIndexes.add(conv.relationshipId)
        }
    }
    /** صدا زده می‌شه از MessageRepositoryImpl بعد از edit/delete پیام */
    suspend fun syncConversationPreviewFromLatestMessage(context: Context, conversationId: String) {
        val messagesResult = MessageRepositoryImpl.getMessages(context, conversationId)
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

    suspend fun resetUnreadCount(
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

    suspend fun clearChatHistory(context: Context, conversationId: String): Result<Unit> {
        return try {
            val messages = MessageRepositoryImpl.getMessages(context, conversationId).getOrDefault(emptyList())
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

            MessageRepositoryImpl.clearLocalCacheKeepPaging(context, conversationId)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteChatForMe(context: Context, conversationId: String, myUid: String): Result<Unit> {
        return try {
            // فقط کش محلی همین دستگاه پاک بشه — پیام‌های سرور دست‌نخورده بمونن،
            // چون بین من و پارتنرم مشترکن و نباید برای اون هم پاک بشه
            MessageRepositoryImpl.stopObservingMessages(conversationId)
            MessageRepositoryImpl.clearLocalCacheAndForget(context, conversationId)

            val ok = FirestoreApiClient.delete(context, "users/$myUid/partner_relationships/$conversationId")
            if (ok) Result.success(Unit) else Result.failure(Exception("حذف چت ناموفق بود"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}