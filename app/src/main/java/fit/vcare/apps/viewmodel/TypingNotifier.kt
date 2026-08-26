package fit.vcare.apps.viewmodel

import android.content.Context
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//TypingNotifier.kt
class TypingNotifier(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var typingActive = false
    private var stopTypingJob: Job? = null

    fun notifyTyping(conversationId: String, uid: String) {
        if (!typingActive) {
            typingActive = true
            scope.launch { ChatRepositoryImpl.setTypingState(context, conversationId, uid, true) }
        }
        stopTypingJob?.cancel()
        stopTypingJob = scope.launch {
            delay(3000L)
            typingActive = false
            ChatRepositoryImpl.setTypingState(context, conversationId, uid, false)
        }
    }

    fun clear(conversationId: String, uid: String) {
        stopTypingJob?.cancel()
        if (typingActive) {
            typingActive = false
            scope.launch { ChatRepositoryImpl.setTypingState(context, conversationId, uid, false) }
        }
    }
}