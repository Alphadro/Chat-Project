package fit.vcare.apps.data.repository

import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageType

//MessagePreviewFormatter.kt
fun previewTextFor(message: Message): String {
    return when (message.type) {
        MessageType.IMAGE -> if (message.text.isNotBlank()) "📷 ${message.text}" else "📷 عکس"
        MessageType.AUDIO -> "🎤 پیام صوتی"
        MessageType.VIDEO -> "🎥 ویدیو"
        MessageType.FILE -> "📎 ${message.fileName ?: "فایل"}"
        MessageType.WALLPAPER_PROPOSAL -> "🖼️ پیشنهاد پس‌زمینه چت"
        else -> message.text
    }
}