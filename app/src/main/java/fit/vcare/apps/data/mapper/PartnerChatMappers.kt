package fit.vcare.apps.data.mapper

import fit.vcare.apps.domain.model.*
import fit.vcare.apps.viewmodel.ChatListItemUiState
import org.json.JSONArray
import org.json.JSONObject
//PartnerChatMappers.kt
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
        ,
        blockedBy = doc.optNullableString("blockedBy")
    )
}

fun Relationship.toJson(): JSONObject = JSONObject().apply {
    put("relationshipId", relationshipId)
    put("userA", userA)
    put("userB", userB)
    put("status", status.name)
    put("createdAt", createdAt)
    put("connectedAt", connectedAt)
    put("blockedBy", blockedBy ?: JSONObject.NULL)
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

fun JSONObject.toPartnerUserInfo(uid: String): PartnerUserInfo {
    val doc = unwrapDocument()
    val email = doc.optString("email", "")
    val name = doc.optString("name", "")
    val photoUrl = doc.optString("profile").ifBlank { null }
    val resolvedName = name.ifBlank { email.ifBlank { uid } }
    return PartnerUserInfo(
        uid = uid,
        email = email,
        displayName = resolvedName,
        photoUrl = photoUrl
    )
}

fun JSONObject.toConversation(fallbackId: String): Conversation {
    val doc = unwrapDocument()
    val ids = mutableListOf<String>()
    doc.optJSONArray("participantIds")?.let { arr ->
        for (i in 0 until arr.length()) ids.add(arr.getString(i))
    }
    val unreadCounts = mutableMapOf<String, Long>()
    ids.forEach { uid ->
        val key = "unread_$uid"
        if (doc.has(key)) unreadCounts[uid] = doc.optLong(key, 0L)
    }
    return Conversation(
        conversationId = doc.optString("conversationId", fallbackId),
        relationshipId = doc.optString("relationshipId"),
        participantIds = ids,
        createdAt = doc.optLong("createdAt"),
        lastMessage = doc.optNullableString("lastMessage"),
        lastMessageAt = if (doc.has("lastMessageAt") && !doc.isNull("lastMessageAt")) doc.optLong("lastMessageAt") else null,
        lastMessageSenderId = doc.optNullableString("lastMessageSenderId"),
        unreadCounts = unreadCounts
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
    unreadCounts.forEach { (uid, count) -> put("unread_$uid", count) }
}
fun JSONObject.toMessage(fallbackId: String, conversationId: String): Message {
    val doc = unwrapDocument()
    val reactionsMap = mutableMapOf<String, String>()
    doc.optJSONObject("reactions")?.let { obj ->
        obj.keys().forEach { uid -> reactionsMap[uid] = obj.optString(uid) }
    }
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
        isEdited = doc.optBoolean("isEdited", false),
        durationMs = if (doc.has("durationMs")) doc.optLong("durationMs") else null,
        mimeType = doc.optString("mimeType").ifBlank { null },
        fileSize = if (doc.has("fileSize")) doc.optLong("fileSize") else null,
        fileName = doc.optString("fileName").ifBlank { null },
        proposalStatus = doc.optString("proposalStatus").ifBlank { null }
            ?.let { runCatching { ProposalStatus.valueOf(it) }.getOrNull() },
        reactions = reactionsMap
,replyToMessageId = doc.optNullableString("replyToMessageId"),
        replyToSenderId = doc.optNullableString("replyToSenderId"),
        replyToText = doc.optNullableString("replyToText"),
        replyToType = doc.optNullableString("replyToType")
            ?.let { runCatching { MessageType.valueOf(it) }.getOrNull() },
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
    put("durationMs", durationMs ?: JSONObject.NULL)
    put("mimeType", mimeType ?: JSONObject.NULL)
    put("fileSize", fileSize ?: JSONObject.NULL)
    put("fileName", fileName ?: JSONObject.NULL)
    put("proposalStatus", proposalStatus?.name ?: JSONObject.NULL)
    put("reactions", JSONObject().apply { reactions.forEach { (uid, emoji) -> put(uid, emoji) } })
    put("replyToMessageId", replyToMessageId ?: JSONObject.NULL)
    put("replyToSenderId", replyToSenderId ?: JSONObject.NULL)
    put("replyToText", replyToText ?: JSONObject.NULL)
    put("replyToType", replyToType?.name ?: JSONObject.NULL)
}
fun ChatListItemUiState.toJson(): JSONObject = JSONObject().apply {
    put("relationshipId", relationshipId)
    put("conversationId", conversationId)
    put("partnerUid", partnerUid)
    put("partnerName", partnerName)
    put("partnerPhotoUrl", partnerPhotoUrl ?: JSONObject.NULL)
    put("lastMessage", lastMessage ?: JSONObject.NULL)
    put("lastMessageAt", lastMessageAt ?: JSONObject.NULL)
}

fun JSONObject.toChatListItem(): ChatListItemUiState = ChatListItemUiState(
    relationshipId = optString("relationshipId"),
    conversationId = optString("conversationId"),
    partnerUid = optString("partnerUid"),
    partnerName = optString("partnerName"),
    partnerPhotoUrl = optNullableString("partnerPhotoUrl"),   // ← تغییر (اگه از قبل داری همینو داشته باش)
    lastMessage = optNullableString("lastMessage"),           // ← تغییر
    lastMessageAt = if (has("lastMessageAt") && !isNull("lastMessageAt")) optLong("lastMessageAt") else null
)

fun List<ChatListItemUiState>.toJsonArray(): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr
}

fun JSONArray.toChatListItems(): List<ChatListItemUiState> {
    val list = mutableListOf<ChatListItemUiState>()
    for (i in 0 until length()) {
        list.add(getJSONObject(i).toChatListItem())
    }
    return list
}
fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.ifBlank { null }
}