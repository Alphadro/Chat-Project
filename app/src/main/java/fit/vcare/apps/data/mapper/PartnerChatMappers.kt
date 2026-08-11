package fit.vcare.apps.data.mapper

import fit.vcare.apps.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.unwrapDocument(): JSONObject =
    this.optJSONObject("document") ?: this

fun JSONObject.toPartnerInvite(fallbackToken: String): PartnerInvite {
    val doc = unwrapDocument()
    return PartnerInvite(
        inviteId = doc.optString("inviteId", fallbackToken),
        token = doc.optString("token", fallbackToken),
        createdBy = doc.optString("createdBy"),
        createdAt = doc.optLong("createdAt"),
        expiresAt = doc.optLong("expiresAt"),
        status = runCatching { InviteStatus.valueOf(doc.optString("status", "ACTIVE")) }
            .getOrDefault(InviteStatus.ACTIVE),
        acceptedBy = doc.optString("acceptedBy").ifBlank { null },
        relationshipId = doc.optString("relationshipId").ifBlank { null }
    )
}

fun PartnerInvite.toJson(): JSONObject = JSONObject().apply {
    put("inviteId", inviteId)
    put("token", token)
    put("createdBy", createdBy)
    put("createdAt", createdAt)
    put("expiresAt", expiresAt)
    put("status", status.name)
    acceptedBy?.let { put("acceptedBy", it) }
    relationshipId?.let { put("relationshipId", it) }
}

fun JSONObject.toRelationship(fallbackId: String): Relationship {
    val doc = unwrapDocument()
    return Relationship(
        relationshipId = doc.optString("relationshipId", fallbackId),
        userA = doc.optString("userA"),
        userB = doc.optString("userB"),
        status = runCatching { RelationshipStatus.valueOf(doc.optString("status", "ACTIVE")) }
            .getOrDefault(RelationshipStatus.ACTIVE),
        createdAt = doc.optLong("createdAt"),
        connectedAt = doc.optLong("connectedAt")
    )
}

fun Relationship.toJson(): JSONObject = JSONObject().apply {
    put("relationshipId", relationshipId)
    put("userA", userA)
    put("userB", userB)
    put("status", status.name)
    put("createdAt", createdAt)
    put("connectedAt", connectedAt)
}

fun JSONObject.toRelationshipIndexEntry(): RelationshipIndexEntry {
    val doc = unwrapDocument()
    return RelationshipIndexEntry(
        relationshipId = doc.optString("relationshipId"),
        partnerUid = doc.optString("partnerUid"),
        status = runCatching { RelationshipStatus.valueOf(doc.optString("status", "ACTIVE")) }
            .getOrDefault(RelationshipStatus.ACTIVE),
        createdAt = doc.optLong("createdAt"),
        connectedAt = doc.optLong("connectedAt")
    )
}

fun RelationshipIndexEntry.toJson(): JSONObject = JSONObject().apply {
    put("relationshipId", relationshipId)
    put("partnerUid", partnerUid)
    put("status", status.name)
    put("createdAt", createdAt)
    put("connectedAt", connectedAt)
}

/**
 * ترتیب نمایش نام: فیلد "name" کاربر (اگر ست شده باشد) -> در غیر این صورت email -> در غیر این صورت uid.
 * هرگز مستقیم email به‌عنوان نام انتخاب نمی‌شود مگر name خالی باشد.
 */
fun JSONObject.toPartnerUserInfo(uid: String): PartnerUserInfo {
    val doc = unwrapDocument()
    val email = doc.optString("email", "")
    val name = doc.optString("name", "")
    val resolvedName = name.ifBlank { email.ifBlank { uid } }
    return PartnerUserInfo(uid = uid, email = email, displayName = resolvedName)
}

fun JSONObject.toConversation(fallbackId: String): Conversation {
    val doc = unwrapDocument()
    val ids = mutableListOf<String>()
    doc.optJSONArray("participantIds")?.let { arr ->
        for (i in 0 until arr.length()) ids.add(arr.getString(i))
    }
    return Conversation(
        conversationId = doc.optString("conversationId", fallbackId),
        relationshipId = doc.optString("relationshipId"),
        participantIds = ids,
        createdAt = doc.optLong("createdAt"),
        lastMessage = doc.optString("lastMessage").ifBlank { null },
        lastMessageAt = if (doc.has("lastMessageAt")) doc.optLong("lastMessageAt") else null,
        lastMessageSenderId = doc.optString("lastMessageSenderId").ifBlank { null }
    )
}

fun Conversation.toJson(): JSONObject = JSONObject().apply {
    put("conversationId", conversationId)
    put("relationshipId", relationshipId)
    put("participantIds", JSONArray(participantIds))
    put("createdAt", createdAt)
    put("lastMessage", lastMessage ?: JSONObject.NULL)
    put("lastMessageAt", lastMessageAt ?: JSONObject.NULL)
    put("lastMessageSenderId", lastMessageSenderId ?: JSONObject.NULL)
}

fun JSONObject.toMessage(fallbackId: String, conversationId: String): Message {
    val doc = unwrapDocument()
    return Message(
        messageId = doc.optString("messageId", fallbackId),
        conversationId = conversationId,
        senderId = doc.optString("senderId"),
        text = doc.optString("text"),
        type = runCatching { MessageType.valueOf(doc.optString("type", "TEXT")) }
            .getOrDefault(MessageType.TEXT),
        mediaUrl = doc.optString("mediaUrl").ifBlank { null },
        createdAt = doc.optLong("createdAt"),
        status = runCatching { MessageStatus.valueOf(doc.optString("status", "SENT")) }
            .getOrDefault(MessageStatus.SENT),
        isEdited = doc.optBoolean("isEdited", false)
    )
}

fun Message.toJson(): JSONObject = JSONObject().apply {
    put("messageId", messageId)
    put("senderId", senderId)
    put("text", text)
    put("type", type.name)
    put("mediaUrl", mediaUrl ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("status", status.name)
    put("isEdited", isEdited)
}