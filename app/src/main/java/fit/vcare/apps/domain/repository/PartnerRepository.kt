package fit.vcare.apps.domain.repository

import android.content.Context
import fit.vcare.apps.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface PartnerRepository {
    suspend fun createInvite(context: Context): Result<PartnerInvite>
    suspend fun getInvite(context: Context, token: String): Result<PartnerInvite>
    suspend fun getUserBasicInfo(context: Context, uid: String): Result<PartnerUserInfo>
    suspend fun acceptInvite(context: Context, token: String): Result<Relationship>
    suspend fun getMyActiveRelationships(context: Context): Result<List<RelationshipIndexEntry>>

    /** بروزرسانی lastActiveAt کاربر جاری (heartbeat سبک برای Presence) — کل document حفظ می‌شود */
    suspend fun sendHeartbeat(context: Context): Result<Unit>

    /** Polling آخرین‌بازدید (lastActiveAt) یک کاربر */
    fun observeUserPresence(
        scope: CoroutineScope,
        context: Context,
        uid: String,
        intervalMs: Long = 15000L
    ): StateFlow<Long?>

    fun stopObservingPresence(uid: String)
}