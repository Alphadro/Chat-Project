package fit.vcare.apps.domain.repository

import android.content.Context
import fit.vcare.apps.domain.model.Conversation
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.ProposalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    suspend fun getOrCreateConversation(
        context: Context,
        relationshipId: String,
        participantIds: List<String>
    ): Result<Conversation>

    suspend fun getConversation(context: Context, conversationId: String): Result<Conversation?>

    suspend fun sendMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        text: String,
        messageId: String? = null
    ): Result<Message>

    suspend fun sendImageMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        caption: String = "",
        messageId: String? = null
    ): Result<Message>

    suspend fun sendAudioMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        mediaUrl: String,
        durationMs: Long,
        mimeType: String,
        fileSize: Long,
        caption: String = "",
        messageId: String? = null
    ): Result<Message>

    suspend fun editMessage(
        context: Context,
        conversationId: String,
        messageId: String,
        newText: String
    ): Result<Unit>

    suspend fun deleteMessage(
        context: Context,
        conversationId: String,
        messageId: String
    ): Result<Unit>

    suspend fun getMessages(context: Context, conversationId: String): Result<List<Message>>

    fun observeMessages(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        intervalMs: Long = 3000L
    ): StateFlow<List<Message>>

    suspend fun refreshNow(context: Context, conversationId: String)

    fun stopObservingMessages(conversationId: String)

    suspend fun updateLastRead(context: Context, conversationId: String, uid: String, lastReadAt: Long): Result<Unit>

    suspend fun setTypingState(context: Context, conversationId: String, uid: String, isTyping: Boolean): Result<Unit>

    fun observePartnerTyping(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        partnerUid: String,
        intervalMs: Long = 2000L
    ): StateFlow<Boolean>

    fun stopObservingTyping(conversationId: String)

    fun observePartnerReadState(
        scope: CoroutineScope,
        context: Context,
        conversationId: String,
        partnerUid: String,
        intervalMs: Long = 3000L
    ): StateFlow<Long?>

    fun stopObservingPartnerReadState(conversationId: String)

    suspend fun sendWallpaperProposal(
        context: Context,
        conversationId: String,
        senderId: String,
        backgroundUrl: String,
        messageId: String? = null
    ): Result<Message>

    suspend fun updateWallpaperProposalStatus(
        context: Context,
        conversationId: String,
        messageId: String,
        status: ProposalStatus
    ): Result<Unit>
    suspend fun clearChatHistory(context: Context, conversationId: String): Result<Unit>

    suspend fun deleteChatForMe(context: Context, conversationId: String, myUid: String): Result<Unit>
}