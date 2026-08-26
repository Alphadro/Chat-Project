package fit.vcare.apps.viewmodel

import android.content.Context
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

//PresenceHeartbeatController.kt
class PresenceHeartbeatController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var heartbeatJob: Job? = null

    fun resume() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var attempts = 0
            while (isActive && attempts < 3) {
                val result = PartnerRepositoryImpl.updatePresence(context, isOnline = true)
                if (result.isSuccess) break
                attempts++
                delay(1500L)
            }
            while (isActive) {
                delay(8000L)
                PartnerRepositoryImpl.updatePresence(context, isOnline = true)
            }
        }
    }

    fun pause() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        scope.launch {
            var attempts = 0
            while (attempts < 2) {
                val result = PartnerRepositoryImpl.updatePresence(context, isOnline = false)
                if (result.isSuccess) break
                attempts++
                delay(800L)
            }
        }
    }
}