package fit.vcare.apps.data.repository

import android.content.Context
import android.util.Log
import fit.vcare.apps.data.mapper.*
import fit.vcare.apps.data.remote.ServerTimeSync
import fit.vcare.apps.domain.model.*
import fit.vcare.apps.domain.repository.PartnerRepository
import fit.vcare.apps.tools.FirestoreApiClient
import fit.vcare.apps.tools.FirestorePoller
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
//PartnerRepositoryImpl.kt
object PartnerRepositoryImpl : PartnerRepository {

    private const val INVITE_TTL_MS = 24 * 60 * 60 * 1000L // 24 ساعت — Assumption

    private val presenceJobs = ConcurrentHashMap<String, Job>()
    private val presenceStates = ConcurrentHashMap<String, MutableStateFlow<PartnerPresence?>>()
    private val relationshipJobs = ConcurrentHashMap<String, Job>()
    private val relationshipStates = ConcurrentHashMap<String, MutableStateFlow<Relationship?>>()

    private fun relationshipIdOf(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    override suspend fun createInvite(context: Context): Result<PartnerInvite> {
        val uid =
            getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))
        val now = ServerTimeSync.now()
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

    private suspend fun writeWithRetry(
        context: Context,
        path: String,
        data: JSONObject,
        attempts: Int = 3
    ): Boolean {
        repeat(attempts) { attempt ->
            if (FirestoreApiClient.write(context, path, data)) return true
            if (attempt < attempts - 1) delay(500L * (attempt + 1)) // 500ms, 1000ms, ...
        }
        return false
    }

    override suspend fun acceptInvite(context: Context, token: String): Result<Relationship> {
        val myUid =
            getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))

        val inviteResult = getInvite(context, token)
        val invite = inviteResult.getOrElse { return Result.failure(it) }

        val now = ServerTimeSync.now()

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
        if (existingRaw != null && !existingRaw.has("error") && existingRaw.unwrapDocument()
                .length() > 0
        ) {
            val existing = existingRaw.toRelationship(relationshipId)
            if (existing.status == RelationshipStatus.ACTIVE) {
                // حتی اگه رابطه از قبل فعال بود، ایندکس هر دو طرف رو دوباره بنویس
                // (شاید یکی از طرفین چت رو حذف کرده و ایندکسش پاک شده باشه)
                val entryForA = RelationshipIndexEntry(
                    relationshipId,
                    existing.otherUid(existing.userA),
                    existing.status,
                    existing.createdAt,
                    now
                )
                val entryForB = RelationshipIndexEntry(
                    relationshipId,
                    existing.otherUid(existing.userB),
                    existing.status,
                    existing.createdAt,
                    now
                )
                writeWithRetry(
                    context,
                    "users/${existing.userA}/partner_relationships/$relationshipId",
                    entryForA.toJson()
                )
                writeWithRetry(
                    context,
                    "users/${existing.userB}/partner_relationships/$relationshipId",
                    entryForB.toJson()
                )
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

        // ── نوشتن حیاتی: بدون این، رابطه‌ای وجود نداره، پس اگه بعد از retry هم fail بشه، کل عملیات fail می‌شه ──
        val relOk = writeWithRetry(context, "relationships/$relationshipId", relationship.toJson())
        if (!relOk) return Result.failure(PartnerException(PartnerError.NetworkError))

        // ── نوشتن‌های best-effort: اگه بعد از retry هم fail بشن، عملیات رو fail نمی‌کنیم چون
        //    relationship اصلی ساخته شده و self-heal بعداً جبران می‌کنه ──
        val updatedInvite = invite.copy(
            status = InviteStatus.ACCEPTED,
            acceptedBy = myUid,
            relationshipId = relationshipId
        )
        val inviteOk = writeWithRetry(context, "partner_invites/$token", updatedInvite.toJson())
        if (!inviteOk) {
            Log.w(
                "PartnerRepository",
                "بروزرسانی وضعیت دعوت‌نامه ناموفق بود بعد از retry — نادیده گرفته شد"
            )
        }

        val entryForA = RelationshipIndexEntry(
            relationshipId,
            relationship.otherUid(relationship.userA),
            relationship.status,
            now,
            now
        )
        val entryForB = RelationshipIndexEntry(
            relationshipId,
            relationship.otherUid(relationship.userB),
            relationship.status,
            now,
            now
        )
        val indexAOk = writeWithRetry(
            context,
            "users/${relationship.userA}/partner_relationships/$relationshipId",
            entryForA.toJson()
        )
        val indexBOk = writeWithRetry(
            context,
            "users/${relationship.userB}/partner_relationships/$relationshipId",
            entryForB.toJson()
        )
        if (!indexAOk || !indexBOk) {
            Log.w(
                "PartnerRepository",
                "نوشتن ایندکس رابطه ناموفق بود بعد از retry (A=$indexAOk, B=$indexBOk) — self-heal بعداً جبران می‌کنه"
            )
        }

        return Result.success(relationship)
    }

    override suspend fun getMyActiveRelationships(context: Context): Result<List<RelationshipIndexEntry>> {
        val uid =
            getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))
        val docs = FirestoreApiClient.list(context, "users/$uid/partner_relationships")
        val entries = docs.map { it.toRelationshipIndexEntry() }
            .filter { it.status == RelationshipStatus.ACTIVE }
        return Result.success(entries)
    }

    /**
     * self-healing: قبلاً اگر خواندن document کاربر fail می‌شد، کل عملیات لغو می‌شد و "آنلاین" هرگز ثبت نمی‌شد.
     * حالا حتی اگر read ناموفق باشد، یک document جدید با فیلدهای حضور ساخته و نوشته می‌شود.
     */
    override suspend fun updatePresence(context: Context, isOnline: Boolean): Result<Unit> {
        val uid =
            getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))
        val path = "users/$uid"
        val now = ServerTimeSync.now()

        val existingRaw = FirestoreApiClient.read(context, path)
        val doc = if (existingRaw != null && !existingRaw.has("error")) {
            val d = existingRaw.unwrapDocument()
            if (d.length() > 0) d else JSONObject()
        } else {
            JSONObject()
        }

        doc.put("isOnline", isOnline)
        doc.put("lastActiveAt", now)

        val ok = FirestoreApiClient.write(context, path, doc)
        return if (ok) Result.success(Unit) else Result.failure(PartnerException(PartnerError.NetworkError))
    }

    override fun observeUserPresence(
        scope: CoroutineScope,
        context: Context,
        uid: String,
        intervalMs: Long
    ): StateFlow<PartnerPresence?> = FirestorePoller.observe(
        scope = scope,
        jobsMap = presenceJobs,
        statesMap = presenceStates,
        key = uid,
        context = context,
        path = "users/$uid",
        intervalMs = intervalMs,
        initialValue = null
    ) { doc ->
        PartnerPresence(
            isOnline = doc.optBoolean("isOnline", false),
            lastActiveAt = doc.optLong("lastActiveAt", 0L)
        )
    }

    override fun stopObservingPresence(uid: String) =
        FirestorePoller.stop(presenceJobs, uid)

    override suspend fun updateRelationshipStatus(
        context: Context,
        relationshipId: String,
        status: RelationshipStatus,
        blockedBy: String?
    ): Result<Unit> {
        val existingRaw = FirestoreApiClient.read(context, "relationships/$relationshipId")
            ?: return Result.failure(PartnerException(PartnerError.NetworkError))
        if (existingRaw.has("error") || existingRaw.unwrapDocument().length() == 0) {
            return Result.failure(PartnerException(PartnerError.NetworkError))
        }

        val relationship = existingRaw.toRelationship(relationshipId)
        val updated = relationship.copy(status = status, blockedBy = blockedBy)

        val ok =
            FirestoreApiClient.write(context, "relationships/$relationshipId", updated.toJson())
        if (!ok) return Result.failure(PartnerException(PartnerError.NetworkError))

        // اگه رابطه واقعاً تموم شده (ENDED)، ایندکس دو طرف هم آپدیت می‌شه تا از لیست چت‌ها حذف بشه
        if (status == RelationshipStatus.ENDED) {
            val now = FirestoreApiClient.getServerTimeMillis()
            val entryForA = RelationshipIndexEntry(
                relationshipId,
                updated.otherUid(updated.userA),
                status,
                updated.createdAt,
                now
            )
            val entryForB = RelationshipIndexEntry(
                relationshipId,
                updated.otherUid(updated.userB),
                status,
                updated.createdAt,
                now
            )
            writeWithRetry(
                context,
                "users/${updated.userA}/partner_relationships/$relationshipId",
                entryForA.toJson()
            )
            writeWithRetry(
                context,
                "users/${updated.userB}/partner_relationships/$relationshipId",
                entryForB.toJson()
            )
        }
        // برای BLOCKED/ACTIVE عمداً ایندکس رو دست نمی‌زنیم — چت باید توی لیست بمونه، فقط داخل چت قفل می‌شه

        return Result.success(Unit)
    }

    override suspend fun reportPartner(
        context: Context,
        relationshipId: String,
        reportedUid: String,
        reason: String
    ): Result<Unit> {
        val myUid =
            getUid(context) ?: return Result.failure(PartnerException(PartnerError.Unauthorized))
        val now = FirestoreApiClient.getServerTimeMillis()
        val reportId = UUID.randomUUID().toString()
        val data = JSONObject().apply {
            put("reportId", reportId)
            put("relationshipId", relationshipId)
            put("reportedBy", myUid)
            put("reportedUid", reportedUid)
            put("reason", reason)
            put("createdAt", now)
        }
        val ok = FirestoreApiClient.write(context, "reports/$reportId", data)
        return if (ok) Result.success(Unit) else Result.failure(PartnerException(PartnerError.NetworkError))
    }

    override fun observeRelationshipStatus(
        scope: CoroutineScope,
        context: Context,
        relationshipId: String,
        intervalMs: Long
    ): StateFlow<Relationship?> = FirestorePoller.observe(
        scope = scope,
        jobsMap = relationshipJobs,
        statesMap = relationshipStates,
        key = relationshipId,
        context = context,
        path = "relationships/$relationshipId",
        intervalMs = intervalMs,
        initialValue = null
    ) { doc -> doc.toRelationship(relationshipId) }

    override fun stopObservingRelationshipStatus(relationshipId: String) =
        FirestorePoller.stop(relationshipJobs, relationshipId)
}
class PartnerException(val error: PartnerError) : Exception(error.message)