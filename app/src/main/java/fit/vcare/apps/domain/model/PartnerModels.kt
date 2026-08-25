package fit.vcare.apps.domain.model

enum class InviteStatus { ACTIVE, ACCEPTED, EXPIRED, CANCELLED }

data class PartnerInvite(
    val inviteId: String,
    val token: String,
    val createdBy: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: InviteStatus,
    val acceptedBy: String? = null,
    val relationshipId: String? = null
)

enum class RelationshipStatus { ACTIVE, ENDED, BLOCKED }

data class Relationship(
    val relationshipId: String,
    val userA: String,
    val userB: String,
    val status: RelationshipStatus,
    val createdAt: Long,
    val connectedAt: Long
    ,
    val blockedBy: String? = null
) {
    fun otherUid(myUid: String): String = if (userA == myUid) userB else userA
}

data class PartnerUserInfo(
    val uid: String,
    val email: String,
    val displayName: String = email,
    val photoUrl: String? = null
)

data class RelationshipIndexEntry(
    val relationshipId: String,
    val partnerUid: String,
    val status: RelationshipStatus,
    val createdAt: Long,
    val connectedAt: Long
)

/** وضعیت حضور صریح کاربر — isOnline مستقیماً روی رفتن اپ به پس‌زمینه/پیش‌زمینه ست می‌شود */
data class PartnerPresence(
    val isOnline: Boolean,
    val lastActiveAt: Long
)

sealed class PartnerError(val message: String) {
    object Unauthorized : PartnerError("لطفاً دوباره وارد شوید")
    object NetworkError : PartnerError("خطا در ارتباط با سرور")
    object InvalidInvite : PartnerError("دعوت‌نامه نامعتبر است")
    object ExpiredInvite : PartnerError("دعوت‌نامه منقضی شده است")
    object CancelledInvite : PartnerError("دعوت‌نامه لغو شده است")
    object AlreadyUsedInvite : PartnerError("این دعوت‌نامه قبلاً استفاده شده است")
    object SelfInvite : PartnerError("نمی‌توانید خودتان را دعوت کنید")
    object AlreadyConnected : PartnerError("شما قبلاً با این کاربر ارتباط دارید")
    data class Unknown(val detail: String) : PartnerError(detail)
}