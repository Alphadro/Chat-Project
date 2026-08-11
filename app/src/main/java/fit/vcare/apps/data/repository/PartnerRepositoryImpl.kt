package fit.vcare.apps.data.repository

import android.content.Context
import fit.vcare.apps.tools.FirestoreApiClient
import fit.vcare.apps.data.mapper.*
import fit.vcare.apps.domain.model.*
import fit.vcare.apps.domain.repository.PartnerRepository
import fit.vcare.apps.login_system.getUid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PartnerRepositoryImpl : PartnerRepository {

    private const val INVITE_TTL_MS = 24 * 60 * 60 * 1000L // 24 ساعت — Assumption

    private val presenceJobs = ConcurrentHashMap<String, Job>()
    private val presenceStates = ConcurrentHashMap<String, MutableStateFlow<Long?>>()

    private fun relationshipIdOf(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    override suspend fun createInvite(context: Context): Result<PartnerInvite> {
        val uid = getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))
        val now = FirestoreApiClient.getServerTimeMillis()
        val token = UUID.randomUUID().toString()

        val invite = PartnerInvite(
            inviteId = token,
            token = token,
            createdBy = uid,
            createdAt = now,
            expiresAt = now + INVITE_TTL_MS,
            status = InviteStatus.ACTIVE
        )

        val ok = FirestoreApiClient.write(context, "partner_invites/$token", invite.toJson())
        return if (ok) Result.success(invite)
        else Result.failure(PartnerException(PartnerError.NetworkError))
    }

    override suspend fun getInvite(context: Context, token: String): Result<PartnerInvite> {
        val raw = FirestoreApiClient.read(context, "partner_invites/$token")
        if (raw == null || raw.has("error")) {
            return Result.failure(PartnerException(PartnerError.InvalidInvite))
        }
        val doc = raw.unwrapDocument()
        if (doc.length() == 0 || !doc.has("token")) {
            return Result.failure(PartnerException(PartnerError.InvalidInvite))
        }
        return Result.success(raw.toPartnerInvite(token))
    }

    override suspend fun getUserBasicInfo(context: Context, uid: String): Result<PartnerUserInfo> {
        val raw = FirestoreApiClient.read(context, "users/$uid")
        if (raw == null || raw.has("error")) {
            return Result.failure(PartnerException(PartnerError.NetworkError))
        }
        return Result.success(raw.toPartnerUserInfo(uid))
    }

    override suspend fun acceptInvite(context: Context, token: String): Result<Relationship> {
        val myUid = getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))

        val inviteResult = getInvite(context, token)
        val invite = inviteResult.getOrElse { return Result.failure(it) }

        val now = FirestoreApiClient.getServerTimeMillis()

        if (invite.status == InviteStatus.CANCELLED) {
            return Result.failure(PartnerException(PartnerError.CancelledInvite))
        }
        if (invite.status == InviteStatus.EXPIRED || now > invite.expiresAt) {
            return Result.failure(PartnerException(PartnerError.ExpiredInvite))
        }
        if (invite.createdBy == myUid) {
            return Result.failure(PartnerException(PartnerError.SelfInvite))
        }

        val relationshipId = relationshipIdOf(invite.createdBy, myUid)

        val existingRaw = FirestoreApiClient.read(context, "relationships/$relationshipId")
        if (existingRaw != null && !existingRaw.has("error") && existingRaw.unwrapDocument().length() > 0) {
            val existing = existingRaw.toRelationship(relationshipId)
            if (existing.status == RelationshipStatus.ACTIVE) {
                return Result.success(existing)
            }
            if (existing.status == RelationshipStatus.BLOCKED) {
                return Result.failure(PartnerException(PartnerError.AlreadyConnected))
            }
        }

        if (invite.status == InviteStatus.ACCEPTED) {
            return Result.failure(PartnerException(PartnerError.AlreadyUsedInvite))
        }

        val relationship = Relationship(
            relationshipId = relationshipId,
            userA = listOf(invite.createdBy, myUid).sorted()[0],
            userB = listOf(invite.createdBy, myUid).sorted()[1],
            status = RelationshipStatus.ACTIVE,
            createdAt = now,
            connectedAt = now
        )

        val relOk = FirestoreApiClient.write(context, "relationships/$relationshipId", relationship.toJson())
        if (!relOk) return Result.failure(PartnerException(PartnerError.NetworkError))

        val updatedInvite = invite.copy(
            status = InviteStatus.ACCEPTED,
            acceptedBy = myUid,
            relationshipId = relationshipId
        )
        FirestoreApiClient.write(context, "partner_invites/$token", updatedInvite.toJson())

        val entryForA = RelationshipIndexEntry(relationshipId, relationship.otherUid(relationship.userA), relationship.status, now, now)
        val entryForB = RelationshipIndexEntry(relationshipId, relationship.otherUid(relationship.userB), relationship.status, now, now)
        FirestoreApiClient.write(context, "users/${relationship.userA}/partner_relationships/$relationshipId", entryForA.toJson())
        FirestoreApiClient.write(context, "users/${relationship.userB}/partner_relationships/$relationshipId", entryForB.toJson())

        return Result.success(relationship)
    }

    override suspend fun getMyActiveRelationships(context: Context): Result<List<RelationshipIndexEntry>> {
        val uid = getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))
        val docs = FirestoreApiClient.list(context, "users/$uid/partner_relationships")
        val entries = docs.map { it.toRelationshipIndexEntry() }
            .filter { it.status == RelationshipStatus.ACTIVE }
        return Result.success(entries)
    }

    override suspend fun sendHeartbeat(context: Context): Result<Unit> {
        val uid = getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))
        val path = "users/$uid"

        val existingRaw = FirestoreApiClient.read(context, path)
            ?: return Result.failure(PartnerException(PartnerError.NetworkError))
        val doc = existingRaw.unwrapDocument()
        if (doc.length() == 0) return Result.failure(PartnerException(PartnerError.NetworkError))

        val now = FirestoreApiClient.getServerTimeMillis()
        // فقط این یک فیلد را عوض می‌کنیم؛ بقیه فیلدهای موجود (email/deviceId/nova/isPermanent/...) دست‌نخورده باقی می‌مانند
        doc.put("lastActiveAt", now)

        val ok = FirestoreApiClient.write(context, path, doc)
        return if (ok) Result.success(Unit) else Result.failure(PartnerException(PartnerError.NetworkError))
    }

    override fun observeUserPresence(
        scope: CoroutineScope,
        context: Context,
        uid: String,
        intervalMs: Long
    ): StateFlow<Long?> {
        val state = presenceStates.getOrPut(uid) { MutableStateFlow(null) }
        presenceJobs[uid]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val raw = FirestoreApiClient.read(context, "users/$uid")
                if (raw != null && !raw.has("error")) {
                    val doc = raw.unwrapDocument()
                    if (doc.length() > 0) {
                        state.value = doc.optLong("lastActiveAt", 0L)
                    }
                }
                delay(intervalMs)
            }
        }
        presenceJobs[uid] = job
        return state.asStateFlow()
    }

    override fun stopObservingPresence(uid: String) {
        presenceJobs[uid]?.cancel()
        presenceJobs.remove(uid)
    }
}

/** Exception حامل PartnerError برای اینکه لایه ViewModel بتواند نوع خطا را تشخیص دهد. */
class PartnerException(val error: PartnerError) : Exception(error.message)