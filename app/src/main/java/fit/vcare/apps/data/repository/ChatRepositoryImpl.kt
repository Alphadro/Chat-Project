package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.domain.model.Conversation
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.ProposalStatus
import fit.vcare.apps.domain.model.ReplyInfo
import fit.vcare.apps.domain.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
//ChatRepositoryImpl.kt
object ChatRepositoryImpl : ChatRepository {

    override suspend fun getOrCreateConversation(
        context: Context,
        relationshipId: String,
        participantIds: List<String>
    ): Result<Conversation> =
        ConversationRepositoryImpl.getOrCreateConversation(context, relationshipId, participantIds)

    override suspend fun getConversation(context: Context, conversationId: String): Result<Conversation?> =
        ConversationRepositoryImpl.getConversation(context, conversationId)

    override suspend fun sendMessage(
        context: Context, conversationId: String, senderId: String, text: String,
        messageId: String?, replyTo: ReplyInfo?
    ): Result<Message> =
        MessageRepositoryImpl.sendMessage(context, conversationId, senderId, text, messageId, replyTo)

    override suspend fun sendImageMessage(
        context: Context, conversationId: String, senderId: String, mediaUrl: String,
        caption: String, messageId: String?, replyTo: ReplyInfo?
    ): Result<Message> =
        MessageRepositoryImpl.sendImageMessage(context, conversationId, senderId, mediaUrl, caption, messageId, replyTo)

    override suspend fun sendAudioMessage(
        context: Context, conversationId: String, senderId: String, mediaUrl: String,
        durationMs: Long, mimeType: String, fileSize: Long, caption: String,
        messageId: String?, replyTo: ReplyInfo?
    ): Result<Message> =
        MessageRepositoryImpl.sendAudioMessage(
            context, conversationId, senderId, mediaUrl, durationMs, mimeType, fileSize, caption, messageId, replyTo
        )

    override suspend fun editMessage(
        context: Context, conversationId: String, messageId: String, newText: String
    ): Result<Unit> =
        MessageRepositoryImpl.editMessage(context, conversationId, messageId, newText)

    override suspend fun deleteMessage(context: Context, conversationId: String, messageId: String): Result<Unit> =
        MessageRepositoryImpl.deleteMessage(context, conversationId, messageId)

    override suspend fun getMessages(context: Context, conversationId: String): Result<List<Message>> =
        MessageRepositoryImpl.getMessages(context, conversationId)

    override fun observeMessages(
        scope: CoroutineScope, context: Context, conversationId: String, viewerUid: String, intervalMs: Long
    ): StateFlow<List<Message>> =
        MessageRepositoryImpl.observeMessages(scope, context, conversationId, viewerUid, intervalMs)

    override suspend fun refreshNow(context: Context, conversationId: String) =
        MessageRepositoryImpl.refreshNow(context, conversationId)

    override fun stopObservingMessages(conversationId: String) =
        MessageRepositoryImpl.stopObservingMessages(conversationId)

    override suspend fun updateLastRead(
        context: Context, conversationId: String, uid: String, lastReadAt: Long
    ): Result<Unit> =
        PresenceTypingRepositoryImpl.updateLastRead(context, conversationId, uid, lastReadAt)

    override suspend fun setTypingState(
        context: Context, conversationId: String, uid: String, isTyping: Boolean
    ): Result<Unit> =
        PresenceTypingRepositoryImpl.setTypingState(context, conversationId, uid, isTyping)

    override fun observePartnerTyping(
        scope: CoroutineScope, context: Context, conversationId: String, partnerUid: String, intervalMs: Long
    ): StateFlow<Boolean> =
        PresenceTypingRepositoryImpl.observePartnerTyping(scope, context, conversationId, partnerUid, intervalMs)

    override fun stopObservingTyping(conversationId: String) =
        PresenceTypingRepositoryImpl.stopObservingTyping(conversationId)

    override fun observePartnerReadState(
        scope: CoroutineScope, context: Context, conversationId: String, partnerUid: String, intervalMs: Long
    ): StateFlow<Long?> =
        PresenceTypingRepositoryImpl.observePartnerReadState(scope, context, conversationId, partnerUid, intervalMs)

    override fun stopObservingPartnerReadState(conversationId: String) =
        PresenceTypingRepositoryImpl.stopObservingPartnerReadState(conversationId)

    override suspend fun sendWallpaperProposal(
        context: Context, conversationId: String, senderId: String, backgroundUrl: String, messageId: String?
    ): Result<Message> =
        MessageRepositoryImpl.sendWallpaperProposal(context, conversationId, senderId, backgroundUrl, messageId)

    override suspend fun updateWallpaperProposalStatus(
        context: Context, conversationId: String, messageId: String, status: ProposalStatus
    ): Result<Unit> =
        MessageRepositoryImpl.updateWallpaperProposalStatus(context, conversationId, messageId, status)

    override suspend fun clearChatHistory(context: Context, conversationId: String): Result<Unit> =
        ConversationRepositoryImpl.clearChatHistory(context, conversationId)

    override suspend fun deleteChatForMe(context: Context, conversationId: String, myUid: String): Result<Unit> =
        ConversationRepositoryImpl.deleteChatForMe(context, conversationId, myUid)

    override suspend fun resetUnreadCount(context: Context, conversationId: String, uid: String): Result<Unit> =
        ConversationRepositoryImpl.resetUnreadCount(context, conversationId, uid)

    override suspend fun getRecentMessages(context: Context, conversationId: String, limit: Int): Result<List<Message>> =
        MessageRepositoryImpl.getRecentMessages(context, conversationId, limit)

    override fun seedCachedMessages(conversationId: String, cachedMessages: List<Message>) =
        MessageRepositoryImpl.seedCachedMessages(conversationId, cachedMessages)

    override suspend fun getMessagesBefore(
        context: Context, conversationId: String, beforeTimestamp: Long, limit: Int
    ): Result<List<Message>> =
        MessageRepositoryImpl.getMessagesBefore(context, conversationId, beforeTimestamp, limit)

    override suspend fun loadOlderMessages(context: Context, conversationId: String): Result<Boolean> =
        MessageRepositoryImpl.loadOlderMessages(context, conversationId)

    override suspend fun toggleMessageReaction(
        context: Context, conversationId: String, messageId: String, uid: String, emoji: String
    ): Result<Unit> =
        MessageRepositoryImpl.toggleMessageReaction(context, conversationId, messageId, uid, emoji)

    override suspend fun sendFileMessage(
        context: Context, conversationId: String, senderId: String, mediaUrl: String,
        fileName: String, fileSize: Long, mimeType: String, caption: String,
        messageId: String?, replyTo: ReplyInfo?
    ): Result<Message> =
        MessageRepositoryImpl.sendFileMessage(
            context, conversationId, senderId, mediaUrl, fileName, fileSize, mimeType, caption, messageId, replyTo
        )

    override suspend fun sendVideoMessage(
        context: Context, conversationId: String, senderId: String, mediaUrl: String,
        durationMs: Long, mimeType: String, fileSize: Long, caption: String,
        messageId: String?, replyTo: ReplyInfo?
    ): Result<Message> =
        MessageRepositoryImpl.sendVideoMessage(
            context, conversationId, senderId, mediaUrl, durationMs, mimeType, fileSize, caption, messageId, replyTo
        )
}