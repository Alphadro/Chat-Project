package fit.vcare.apps.data.audio


import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * مسئول start/stop/cancel/release ضبط صدا با MediaRecorder استاندارد.
 * خروجی: فایل MPEG_4 (container) + AAC (codec) یعنی .m4a — کیفیت مناسب صدا، حجم کم، سازگاری بالا.
 * فایل موقت داخل cacheDir ساخته می‌شود.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    /** true اگر با موفقیت شروع شد */
    fun start(): Boolean {
        if (isRecording) return false

        return try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            isRecording = true
            true
        } catch (e: IOException) {
            releaseInternal()
            false
        } catch (e: IllegalStateException) {
            releaseInternal()
            false
        } catch (e: RuntimeException) {
            // معمولاً یعنی میکروفون در دسترس نیست (توسط اپ دیگری اشغال شده)
            releaseInternal()
            false
        }
    }

    /**
     * توقف عادی ضبط. فایل نهایی را برمی‌گرداند (با durationMs واقعی که از خود فایل خوانده می‌شود).
     * اگر توقف با خطا مواجه شد یا فایل خیلی کوتاه/خالی بود، null برمی‌گرداند.
     */
    fun stop(): RecordedAudio? {
        if (!isRecording) return null

        val file = outputFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            isRecording = false
            recorder = null

            if (file == null || !file.exists() || file.length() <= 0L) {
                file?.delete()
                return null
            }

            val durationMs = readDurationMs(file)
            RecordedAudio(file = file, durationMs = durationMs, mimeType = "audio/mp4")
        } catch (e: RuntimeException) {
            // stop() ممکن است اگر ضبط خیلی کوتاه بوده (کمتر از چند صد میلی‌ثانیه) exception بدهد
            releaseInternal()
            file?.delete()
            null
        }
    }

    /** لغو ضبط — فایل موقت حذف می‌شود، چیزی ارسال نمی‌شود */
    fun cancel() {
        releaseInternal()
        outputFile?.delete()
        outputFile = null
    }

    /** برای اطمینان از آزادسازی کامل منابع (مثلاً هنگام خروج از صفحه یا onCleared ViewModel) */
    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        try {
            if (isRecording) {
                recorder?.stop()
            }
        } catch (_: RuntimeException) {
            // نادیده گرفته می‌شود — ممکن است چون هنوز چیزی ضبط نشده stop() fail کند
        }
        try {
            recorder?.release()
        } catch (_: RuntimeException) {
        }
        recorder = null
        isRecording = false
    }

    private fun readDurationMs(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

data class RecordedAudio(
    val file: File,
    val durationMs: Long,
    val mimeType: String
)