package fit.vcare.apps.domain.model
//ChatModels.kt
enum class MessageType { TEXT, IMAGE, VIDEO, FILE, AUDIO, SYSTEM, WALLPAPER_PROPOSAL }
enum class MessageStatus { PENDING, SENT, DELIVERED, READ }
enum class ProposalStatus { PENDING, ACCEPTED, REJECTED }

data class Conversation(
    val conversationId: String,
    val relationshipId: String,
    val participantIds: List<String>,
    val createdAt: Long,
    val lastMessage: String? = null,
    val lastMessageAt: Long? = null,
    val lastMessageSenderId: String? = null,
    val unreadCounts: Map<String, Long> = emptyMap()
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
    // فقط برای MessageType.AUDIO
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val fileName: String? = null,
    // فقط برای MessageType.WALLPAPER_PROPOSAL — mediaUrl همون آدرس عکس پیشنهادیه
    val proposalStatus: ProposalStatus? = null,
    val reactions: Map<String, String> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null,
    val replyToType: MessageType? = null,
)
/** برای پاس دادن اطلاعات پیامِ در حال ریپلای بین ViewModel و Repository */
data class ReplyInfo(
    val messageId: String,
    val senderId: String,
    val text: String,
    val type: MessageType
)
data class ReadState(
    val uid: String,
    val lastReadAt: Long
)