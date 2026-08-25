package fit.vcare.apps.room

import androidx.room.Entity
import androidx.room.Index
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.ProposalStatus
import org.json.JSONObject
//MessageEntity.kt
@Entity(
    tableName = "messages",
    primaryKeys = ["messageId"],
    indices = [Index(value = ["conversationId", "createdAt"])] // برای کوئری‌های صفحه‌بندی سریع
)
data class MessageEntity(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val type: String,
    val mediaUrl: String?,
    val createdAt: Long,
    val status: String,
    val isEdited: Boolean,
    val durationMs: Long?,
    val mimeType: String?,
    val fileSize: Long?,
    val fileName: String?,
    val proposalStatus: String?,
    val reactionsJson: String = "{}",
    val replyToMessageId: String?,
    val replyToSenderId: String?,
    val replyToText: String?,
    val replyToType: String?,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    messageId = messageId, conversationId = conversationId, senderId = senderId, text = text,
    type = type.name, mediaUrl = mediaUrl, createdAt = createdAt, status = status.name,
    isEdited = isEdited, durationMs = durationMs, mimeType = mimeType, fileSize = fileSize,   fileName = fileName,
    proposalStatus = proposalStatus?.name,  reactionsJson = JSONObject(reactions as Map<*, *>).toString(),  replyToMessageId = replyToMessageId, replyToSenderId = replyToSenderId,
    replyToText = replyToText, replyToType = replyToType?.name,
)

fun MessageEntity.toMessage(): Message {
    val reactionsMap = runCatching {
        val obj = JSONObject(reactionsJson)
        obj.keys().asSequence().associateWith { obj.optString(it) }
    }.getOrDefault(emptyMap())
    return Message(
    messageId = messageId, conversationId = conversationId, senderId = senderId, text = text,
    type = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT),
    mediaUrl = mediaUrl, createdAt = createdAt,
    status = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SENT),
    isEdited = isEdited, durationMs = durationMs, mimeType = mimeType, fileSize = fileSize, fileName = fileName,
        proposalStatus = proposalStatus?.let { runCatching { ProposalStatus.valueOf(it) }.getOrNull() },
    reactions = reactionsMap, replyToMessageId = replyToMessageId, replyToSenderId = replyToSenderId,
        replyToText = replyToText,
        replyToType = replyToType?.let { runCatching { MessageType.valueOf(it) }.getOrNull() },

        )}