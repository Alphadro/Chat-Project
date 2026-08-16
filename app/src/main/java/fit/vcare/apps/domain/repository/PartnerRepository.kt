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

    /**
     * بروزرسانی صریح وضعیت حضور کاربر جاری.
     * isOnline=true وقتی چت باز و اپ در پیش‌زمینه است، isOnline=false وقتی چت بسته یا اپ در پس‌زمینه رفت.
     * lastActiveAt همیشه با now بروزرسانی می‌شود (برای نمایش "آخرین بازدید").
     */
    suspend fun updatePresence(context: Context, isOnline: Boolean): Result<Unit>

    fun observeUserPresence(
        scope: CoroutineScope,
        context: Context,
        uid: String,
        intervalMs: Long = 10000L
    ): StateFlow<PartnerPresence?>

    fun stopObservingPresence(uid: String)
}