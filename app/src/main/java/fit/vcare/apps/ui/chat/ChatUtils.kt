package fit.vcare.apps.ui.chat

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.viewmodel.ChatUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

//ChatUtils.kt
 val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
 const val PRESENCE_STALE_MS = 20_000L
 const val MAX_MESSAGE_INPUT_CHARS = 4000
 val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")


sealed class ChatListEntry {
    data class MessageEntry(val message: Message) : ChatListEntry()
    data class DateHeaderEntry(val label: String, val dateKey: String) : ChatListEntry()
}



private fun dayKey(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

private fun dateHeaderLabel(millis: Long): String {
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = millis }

    val isSameDay = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) return "امروز"

    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterdayCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "دیروز"

    return SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(millis))
}

fun indexOfMessageInReversed(reversedEntries: List<ChatListEntry>, messageId: String): Int =
    reversedEntries.indexOfFirst { it is ChatListEntry.MessageEntry && it.message.messageId == messageId }

 fun buildChatEntries(messages: List<Message>): List<ChatListEntry> {
    val result = mutableListOf<ChatListEntry>()
    var lastDayKey: String? = null
    for (msg in messages) {
        val key = dayKey(msg.createdAt)
        if (key != lastDayKey) {
            result.add(ChatListEntry.DateHeaderEntry(dateHeaderLabel(msg.createdAt), key))
            lastDayKey = key
        }
        result.add(ChatListEntry.MessageEntry(msg))
    }
    return result
}

 fun formatMessageTime(millis: Long): String =
    if (millis <= 0) "" else timeFormatter.format(Date(millis))

 fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

 fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}

 fun partnerStatusText(uiState: ChatUiState): String {
    if (uiState.partnerIsTyping) return "در حال تایپ..."
    val presence = uiState.partnerPresence ?: return ""
    val isFresh = presence.lastActiveAt > 0 &&
            (System.currentTimeMillis() - presence.lastActiveAt) < PRESENCE_STALE_MS
    return when {
        presence.isOnline && isFresh -> "آنلاین"
        presence.lastActiveAt > 0 -> formatLastSeen(presence.lastActiveAt)
        else -> ""
    }
}

private fun formatLastSeen(lastActiveAt: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - lastActiveAt

    val nowCal = Calendar.getInstance()
    val seenCal = Calendar.getInstance().apply { timeInMillis = lastActiveAt }
    val isSameDay = nowCal.get(Calendar.YEAR) == seenCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == seenCal.get(Calendar.DAY_OF_YEAR)

    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterdayCal.get(Calendar.YEAR) == seenCal.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == seenCal.get(Calendar.DAY_OF_YEAR)

    val timeStr = timeFormatter.format(Date(lastActiveAt))

    return when {
        isSameDay -> "آخرین بازدید امروز ساعت $timeStr"
        isYesterday -> "آخرین بازدید دیروز ساعت $timeStr"
        diff < 7L * 24 * 60 * 60 * 1000L -> {
            val dayFormatter = SimpleDateFormat("EEEE", Locale("fa"))
            "آخرین بازدید ${dayFormatter.format(Date(lastActiveAt))} ساعت $timeStr"
        }

        diff < 30L * 24 * 60 * 60 * 1000L -> "آخرین بازدید در یک ماه اخیر"
        else -> "آخرین بازدید خیلی وقت پیش"
    }
}

 val IMAGE_MAX_WIDTH = 240.dp
 val IMAGE_MAX_HEIGHT = 320.dp
 val IMAGE_MIN_WIDTH = 120.dp
 val IMAGE_MIN_HEIGHT = 120.dp

/**
 * منطق شبیه تلگرام: عکس رو با حفظ نسبت ابعاد داخل یک باکس max×max جا می‌ده.
 * اگه نسبت خیلی افراطی باشه (خیلی دراز یا خیلی پهن)، یک ضلع روی min قفل می‌شه
 * و عکس با ContentScale.Crop از داخل باکس تنظیم می‌شه (بدون کش اومدن/تغییر شکل).
 */
 fun computeBubbleImageSize(
    naturalWidth: Int,
    naturalHeight: Int
): Triple<Dp, Dp, ContentScale> {
    if (naturalWidth <= 0 || naturalHeight <= 0) {
        return Triple(IMAGE_MAX_WIDTH, IMAGE_MAX_HEIGHT, ContentScale.Crop)
    }
    val ratio = naturalWidth.toFloat() / naturalHeight.toFloat()

    var w = IMAGE_MAX_WIDTH.value
    var h = w / ratio

    if (h in IMAGE_MIN_HEIGHT.value..IMAGE_MAX_HEIGHT.value) {
        return Triple(w.dp, h.dp, ContentScale.Fit)
    }

    if (h > IMAGE_MAX_HEIGHT.value) {
        h = IMAGE_MAX_HEIGHT.value
        w = h * ratio
        return if (w < IMAGE_MIN_WIDTH.value) {
            Triple(IMAGE_MIN_WIDTH, IMAGE_MAX_HEIGHT, ContentScale.Crop)
        } else {
            Triple(w.dp, h.dp, ContentScale.Fit)
        }
    }

    h = IMAGE_MIN_HEIGHT.value
    w = h * ratio
    return if (w > IMAGE_MAX_WIDTH.value) {
        Triple(IMAGE_MAX_WIDTH, IMAGE_MIN_HEIGHT, ContentScale.Crop)
    } else {
        Triple(w.dp, h.dp, ContentScale.Fit)
    }
}
