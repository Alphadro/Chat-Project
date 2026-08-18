package fit.vcare.apps.data.audio


import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlaybackState(
    val playingMessageId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Singleton سراسری اپ — تضمین می‌کند در کل اپ فقط یک پیام صوتی هم‌زمان پخش شود.
 * هر بار پیام جدیدی Play می‌شود، پخش قبلی متوقف/جایگزین می‌شود.
 */
object AudioPlayerManager {

    private var exoPlayer: ExoPlayer? = null
    private var currentMessageId: String? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state

    private fun ensurePlayer(context: Context): ExoPlayer {
        var player = exoPlayer
        if (player == null) {
            player = ExoPlayer.Builder(context.applicationContext).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.value = _state.value.copy(isPlaying = false, positionMs = _state.value.durationMs)
                        stopProgressLoop()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                    if (isPlaying) startProgressLoop() else stopProgressLoop()
                }
            })
            exoPlayer = player
        }
        return player
    }

    /** اگر همان پیام در حال پخش است، Pause/Resume می‌کند. اگر پیام دیگری است، پخش قبلی را جایگزین می‌کند. */
    fun playOrToggle(context: Context, messageId: String, url: String) {
        val player = ensurePlayer(context)

        if (currentMessageId == messageId) {
            if (player.playbackState == Player.STATE_ENDED) {
                // ویس تموم شده -> برای replay باید اول به ابتدا برگرده
                player.seekTo(0)
                _state.value = _state.value.copy(positionMs = 0L)
                player.play()
                return
            }
            if (player.isPlaying) {
                pause()
            } else {
                player.play()
            }
            return
        }

        currentMessageId = messageId
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true

        _state.value = AudioPlaybackState(
            playingMessageId = messageId,
            isPlaying = true,
            positionMs = 0L,
            durationMs = 0L
        )
    }

    fun pause() {
        exoPlayer?.pause()
        exoPlayer?.let { player ->
            _state.value = _state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0L))
        }
    }
    fun stop() {
        exoPlayer?.stop()
        currentMessageId = null
        stopProgressLoop()
        _state.value = AudioPlaybackState()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && currentMessageId != null) {
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L)
                    )
                }
                delay(80L)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    /** هنگام خروج کامل از ChatScreen صدا زده می‌شود */
    fun release() {
        stopProgressLoop()
        exoPlayer?.release()
        exoPlayer = null
        currentMessageId = null
        _state.value = AudioPlaybackState()
    }
}