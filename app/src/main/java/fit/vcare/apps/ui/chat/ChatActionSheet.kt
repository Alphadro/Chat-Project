package fit.vcare.apps.ui.chat


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType

//ChatActionSheet.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    message: Message,
    currentUid: String,
    onDismiss: () -> Unit,
    onToggleReaction: (Message, String) -> Unit,
    onReply: (Message) -> Unit,
    onSaveImage: (String) -> Unit,
    onDownloadFile: (String, String) -> Unit,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    onShareMedia: (Message) -> Unit,   // ← جدید
    onStartEditing: (Message) -> Unit,
    onDelete: (Message) -> Unit
) {
    val isMine = message.senderId == currentUid
    val isImage = message.type == MessageType.IMAGE
    val isVideo = message.type == MessageType.VIDEO
    val isAudio = message.type == MessageType.AUDIO
    val isWallpaperProposal = message.type == MessageType.WALLPAPER_PROPOSAL

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {

            if (message.status != MessageStatus.PENDING) {
                val myReaction = message.reactions[currentUid]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        val isSelected = emoji == myReaction
                        Text(
                            text = emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (isSelected)
                                        Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.15f
                                                )
                                            )
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                    else Modifier
                                )
                                .clickable {
                                    onToggleReaction(message, emoji)
                                    onDismiss()
                                }
                                .padding(6.dp)
                        )
                    }
                }
                Divider()
            }
            ListItem(
                headlineContent = { Text("پاسخ") },
                leadingContent = { Icon(Icons.Filled.Reply, contentDescription = null) },
                modifier = Modifier.clickable { onReply(message); onDismiss() }
            )

            if (isImage && message.status != MessageStatus.PENDING && !message.mediaUrl.isNullOrBlank()) {
                ListItem(
                    headlineContent = { Text("ذخیره در گالری") },
                    leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                    modifier = Modifier.clickable { onSaveImage(message.mediaUrl!!); onDismiss() }
                )
                ListItem(
                    headlineContent = { Text("اشتراک‌گذاری") },
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.clickable { onShareMedia(message); onDismiss() }
                )
            }

            if (isVideo && message.status != MessageStatus.PENDING && !message.mediaUrl.isNullOrBlank()) {
                ListItem(
                    headlineContent = { Text("دانلود ویدیو") },
                    leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDownloadFile(message.mediaUrl!!, "video_${message.messageId}.mp4")
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text("اشتراک‌گذاری") },
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.clickable { onShareMedia(message); onDismiss() }
                )
            }
            if (message.type == MessageType.FILE && message.status != MessageStatus.PENDING && !message.mediaUrl.isNullOrBlank()) {
                ListItem(
                    headlineContent = { Text("دانلود فایل") },
                    leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDownloadFile(message.mediaUrl!!, message.fileName ?: "file")
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text("اشتراک‌گذاری") },
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.clickable { onShareMedia(message); onDismiss() }
                )
            }

            if (!isImage && !isVideo && !isAudio && !isWallpaperProposal) {
                ListItem(
                    headlineContent = { Text("Copy") },
                    leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    modifier = Modifier.clickable { onCopyText(message.text); onDismiss() }
                )
                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.clickable { onShareText(message.text); onDismiss() }
                )
            }

            if (isMine) {
                if (!isWallpaperProposal) {
                    ListItem(
                        headlineContent = { Text(if (isImage || isVideo) "Edit Caption" else "Edit") },
                        leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        modifier = Modifier.clickable { onStartEditing(message); onDismiss() }
                    )
                }
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { onDelete(message); onDismiss() }
                )
            }
        }
    }
}