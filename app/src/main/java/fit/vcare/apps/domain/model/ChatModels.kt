package fit.vcare.apps.domain.model

enum class MessageType { TEXT, IMAGE, VIDEO, FILE, AUDIO, SYSTEM }
enum class MessageStatus { PENDING, SENT, DELIVERED, READ }

data class Conversation(
    val conversationId: String,
    val relationshipId: String,
    val participantIds: List<String>,
    val createdAt: Long,
    val lastMessage: String? = null,
    val lastMessageAt: Long? = null,
    val lastMessageSenderId: String? = null
)

data class Message(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String? = null,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val isEdited: Boolean = false,
    // فقط برای MessageType.AUDIO پر می‌شوند
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null
)

data class ReadState(
    val uid: String,
    val lastReadAt: Long
)