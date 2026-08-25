package fit.vcare.apps.data.remote

import android.content.Context
import fit.vcare.apps.tools.FirestoreApiClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//ServerTimeSync
object ServerTimeSync {

    @Volatile private var offsetMs: Long = 0L
    @Volatile private var lastSyncedAt: Long = 0L
    private val mutex = Mutex()

    private const val RESYNC_INTERVAL_MS = 5 * 60 * 1000L // هر ۵ دقیقه دوباره sync کن

    /** تخمین ساعت سرور بر اساس آخرین افست شناخته‌شده — همیشه فوری، بدون تأخیر شبکه */
    fun now(): Long = System.currentTimeMillis() + offsetMs

    /** موقع باز شدن چت صدا زده می‌شه؛ اگه اخیراً sync شده بود، کاری نمی‌کنه */
    suspend fun ensureSynced(context: Context) {
        val elapsed = System.currentTimeMillis() - lastSyncedAt
        if (lastSyncedAt != 0L && elapsed < RESYNC_INTERVAL_MS) return

        mutex.withLock {
            val elapsedInsideLock = System.currentTimeMillis() - lastSyncedAt
            if (lastSyncedAt != 0L && elapsedInsideLock < RESYNC_INTERVAL_MS) return

            val beforeRequest = System.currentTimeMillis()
            val serverTime = FirestoreApiClient.getServerTimeMillis()
            val afterRequest = System.currentTimeMillis()

            // نصف round-trip به‌عنوان تاخیر شبکه در نظر گرفته می‌شه تا تخمین دقیق‌تر بشه
            val roundTrip = (afterRequest - beforeRequest).coerceAtLeast(0L)
            val estimatedDeviceTimeAtServerResponse = beforeRequest + roundTrip / 2

            offsetMs = serverTime - estimatedDeviceTimeAtServerResponse
            lastSyncedAt = System.currentTimeMillis()
        }
    }
}