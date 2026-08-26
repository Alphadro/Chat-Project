package fit.vcare.apps.ui.chat


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageType
//ChatInputBar.kt
@Composable
fun ChatErrorBanner(error: String?) {
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
    }
}

@Composable
fun ChatRecordingErrorBanner(recordingError: String?, onClear: () -> Unit) {
    recordingError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000L)
            onClear()
        }
    }
}

@Composable
fun ReplyPreviewBar(
    replyingToMessage: Message,
    currentUid: String,
    partnerName: String,
    myBubbleColor: Color,
    onCancelReply: () -> Unit
) {
    val replySenderLabel = if (replyingToMessage.senderId == currentUid) "شما" else partnerName
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(myBubbleColor)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "پاسخ به $replySenderLabel",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = myBubbleColor
            )
            Text(
                text = when (replyingToMessage.type) {
                    MessageType.IMAGE -> if (replyingToMessage.text.isNotBlank()) replyingToMessage.text else "📷 عکس"
                    MessageType.AUDIO -> "🎤 پیام صوتی"
                    MessageType.FILE -> replyingToMessage.fileName ?: "📎 فایل"
                    else -> replyingToMessage.text
                },
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCancelReply) {
            Icon(Icons.Filled.Close, contentDescription = "لغو ریپلای")
        }
    }
}

@Composable
fun EditingPreviewBar(onCancelEditing: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("در حال ویرایش پیام", fontSize = 12.sp)
        TextButton(onClick = onCancelEditing) { Text("لغو") }
    }
}

@Composable
fun ChatBottomBar(
    isBlockedByMe: Boolean,
    isBlockedByPartner: Boolean,
    partnerName: String,
    onUnblock: () -> Unit,
    isRecording: Boolean,
    recordingDurationMs: Long,
    onCancelRecording: () -> Unit,
    onStopRecording: () -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    editingMessageId: String?,
    onNotifyTyping: () -> Unit,
    onEmojiClick: () -> Unit,
    onMicClick: () -> Unit,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit,
    onSend: () -> Unit,
    isUploadingImage: Boolean,
    isUploadingVideo: Boolean,
    isUploadingFile: Boolean,
    isUploadingAudio: Boolean,
    isSending: Boolean,
    textFieldFocusRequester: FocusRequester,
    onTextFieldFocused: () -> Unit
) {
    val context = LocalContext.current

    when {
        isBlockedByMe -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "شما این پارتنر را مسدود کرده‌اید",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onUnblock) { Text("لغو مسدودیت") }
            }
        }

        isBlockedByPartner -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$partnerName شما را مسدود کرده است",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        isRecording -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancelRecording) {
                    Icon(Icons.Filled.Close, contentDescription = "لغو ضبط", tint = MaterialTheme.colorScheme.error)
                }
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = formatDuration(recordingDurationMs), fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onStopRecording) {
                    Icon(Icons.Filled.Send, contentDescription = "توقف و پیش‌نمایش", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onEmojiClick) {
                    Icon(Icons.Filled.EmojiEmotions, contentDescription = "Emoji", tint = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { newValue ->
                        if (newValue.length <= MAX_MESSAGE_INPUT_CHARS) {
                            onMessageTextChange(newValue)
                            onNotifyTyping()
                        } else {
                            onMessageTextChange(newValue.take(MAX_MESSAGE_INPUT_CHARS))
                            Toast.makeText(
                                context,
                                "حداکثر $MAX_MESSAGE_INPUT_CHARS کاراکتر مجاز است",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 200.dp)
                        .heightIn(min = 48.dp, max = 120.dp)
                        .focusRequester(textFieldFocusRequester) .onFocusChanged { focusState ->            // ← جدید
                            if (focusState.isFocused) {
                                onTextFieldFocused()
                            }
                        },
                    placeholder = { Text("Type a message...") }
                )

                Spacer(Modifier.width(4.dp))

                IconButton(onClick = onPickMedia, enabled = !isUploadingImage && !isUploadingVideo) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Add Media", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onPickFile, enabled = !isUploadingFile) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Add File", tint = MaterialTheme.colorScheme.primary)
                }
                if (messageText.isBlank() && editingMessageId == null) {
                    IconButton(onClick = onMicClick, enabled = !isUploadingAudio) {
                        Icon(Icons.Filled.Mic, contentDescription = "Record Voice Message", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = onSend, enabled = !isSending) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}