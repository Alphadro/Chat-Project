package fit.vcare.apps.viewmodel

import android.content.Context
import fit.vcare.apps.data.audio.AudioRecorder
import fit.vcare.apps.data.audio.RecordedAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

//VoiceRecordingController.kt
data class VoiceRecordingUiState(
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L
)

class VoiceRecordingController(
    context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val MAX_RECORDING_DURATION_MS = 120_000L
    }

    private val audioRecorder = AudioRecorder(context)
    private var recordingTimerJob: Job? = null

    private val _state = MutableStateFlow(VoiceRecordingUiState())
    val state: StateFlow<VoiceRecordingUiState> = _state

    /** true اگر ضبط با موفقیت شروع شد */
    fun start(onMaxDurationReached: () -> Unit): Boolean {
        if (_state.value.isRecording) return false
        val started = audioRecorder.start()
        if (!started) return false

        _state.value = VoiceRecordingUiState(isRecording = true, recordingDurationMs = 0L)

        recordingTimerJob?.cancel()
        recordingTimerJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive && _state.value.isRecording) {
                val elapsed = System.currentTimeMillis() - startedAt
                _state.value = _state.value.copy(recordingDurationMs = elapsed)
                if (elapsed >= MAX_RECORDING_DURATION_MS) {
                    onMaxDurationReached()
                    break
                }
                delay(200L)
            }
        }
        return true
    }

    /** ضبط رو متوقف می‌کنه؛ خروجی: (فایل ضبط‌شده یا null، مدت‌زمان سپری‌شده تا لحظه‌ی توقف) */
    fun stop(): Pair<RecordedAudio?, Long> {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        val elapsedMs = _state.value.recordingDurationMs
        val recorded = audioRecorder.stop()
        _state.value = VoiceRecordingUiState(isRecording = false, recordingDurationMs = 0L)
        return recorded to elapsedMs
    }

    fun cancel() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        audioRecorder.cancel()
        _state.value = VoiceRecordingUiState(isRecording = false, recordingDurationMs = 0L)
    }

    fun release() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        if (_state.value.isRecording) audioRecorder.cancel()
        audioRecorder.release()
    }
}