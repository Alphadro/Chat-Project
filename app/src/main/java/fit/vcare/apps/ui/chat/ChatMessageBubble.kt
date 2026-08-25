package fit.vcare.apps.ui.chat


import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fit.vcare.apps.data.audio.AudioPlayerManager
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.ProposalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

//ChatMessageBubble.kt
@Composable
fun MessageBubbleRow(
    message: Message,
    isMine: Boolean,
    isPending: Boolean,
    isHighlighted: Boolean,
    myBubbleColor: Color,
    currentUid: String,
    partnerName: String,
    partnerLastReadAt: Long?,
    replyThresholdPx: Float,
    reversedEntries: List<ChatListEntry>,
    coroutineScope: CoroutineScope,
    listState: LazyListState,
    onReply: (Message) -> Unit,
    onLongPress: (Message) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onToggleAudio: (Message) -> Unit,
    onDownloadFile: (String, String) -> Unit,
    onSaveImage: (String) -> Unit,
    onRespondToProposal: (Message, Boolean) -> Unit
) {
    val audioPlaybackState by AudioPlayerManager.state.collectAsState()
    val context = LocalContext.current

    var dragOffsetX by remember(message.messageId) { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = dragOffsetX, label = "replyDrag")
    val replyIconAlpha = (kotlin.math.abs(dragOffsetX) / replyThresholdPx).coerceIn(0f, 1f)

    val highlightModifier = if (isHighlighted) {
        Modifier.border(2.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp))
    } else Modifier

    fun scrollToMessage(messageId: String) {
        val idx = indexOfMessageInReversed(reversedEntries, messageId)
        if (idx >= 0) coroutineScope.launch { listState.animateScrollToItem(idx) }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (!isPending) {
            Icon(
                imageVector = Icons.Filled.Reply,
                contentDescription = "ریپلای",
                tint = myBubbleColor.copy(alpha = replyIconAlpha),
                modifier = Modifier
                    .align(if (isMine) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(message.messageId, isPending) {
                    if (isPending) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffsetX) >= replyThresholdPx) onReply(message)
                            dragOffsetX = 0f
                        },
                        onDragCancel = { dragOffsetX = 0f },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + delta).coerceIn(
                                -replyThresholdPx * 1.3f,
                                replyThresholdPx * 1.3f
                            )
                        }
                    )
                },
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            when (message.type) {
                MessageType.FILE -> FileMessageBubble(
                    message = message,
                    isMine = isMine,
                    isPending = isPending,
                    highlightModifier = highlightModifier,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onLongPress = onLongPress,
                    onDownloadFile = onDownloadFile,
                    onScrollToReply = { scrollToMessage(it) }
                )

                MessageType.VIDEO -> VideoMessageBubble(
                    message = message,
                    isMine = isMine,
                    isPending = isPending,
                    highlightModifier = highlightModifier,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onVideoClick = onVideoClick,
                    onDownloadFile = onDownloadFile,
                    onLongPress = onLongPress,
                    onScrollToReply = { scrollToMessage(it) }
                )

                MessageType.WALLPAPER_PROPOSAL -> WallpaperProposalBubble(
                    message = message,
                    isMine = isMine,
                    highlightModifier = highlightModifier,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onScrollToReply = { scrollToMessage(it) },
                    onRespond = onRespondToProposal
                )

                MessageType.IMAGE -> ImageMessageBubble(
                    message = message,
                    isMine = isMine,
                    isPending = isPending,
                    highlightModifier = highlightModifier,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onLongPress = onLongPress,
                    onImageClick = onImageClick,
                    onSaveImage = onSaveImage,
                    onScrollToReply = { scrollToMessage(it) }
                )

                MessageType.AUDIO -> AudioMessageBubble(
                    message = message,
                    isMine = isMine,
                    isPending = isPending,
                    highlightModifier = highlightModifier,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    audioPlaybackState = audioPlaybackState,
                    onLongPress = onLongPress,
                    onToggleAudio = onToggleAudio,
                    onScrollToReply = { scrollToMessage(it) }
                )

                else -> TextMessageBubble(
                    message = message,
                    isMine = isMine,
                    isPending = isPending,
                    highlightModifier = highlightModifier,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onLongPress = onLongPress,
                    onScrollToReply = { scrollToMessage(it) }
                )
            }

            if (message.reactions.isNotEmpty()) {
                val grouped = message.reactions.values.groupingBy { it }.eachCount()
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    grouped.forEach { (emoji, count) ->
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = if (count > 1) "$emoji $count" else emoji, fontSize = 13.sp)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatMessageTime(message.createdAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isMine) {
                    Spacer(Modifier.width(4.dp))
                    when {
                        isPending -> {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = "در حال ارسال",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> {
                            val isReadByPartner =
                                partnerLastReadAt != null && partnerLastReadAt >= message.createdAt
                            val icon = when {
                                isReadByPartner -> Icons.Filled.DoneAll
                                message.status == MessageStatus.DELIVERED -> Icons.Filled.DoneAll
                                else -> Icons.Filled.Done
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = if (isReadByPartner) "خوانده شد" else if (message.status == MessageStatus.DELIVERED) "رسید" else "ارسال شد",
                                modifier = Modifier.size(14.dp),
                                tint = if (isReadByPartner) myBubbleColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileMessageBubble(
    message: Message, isMine: Boolean, isPending: Boolean, highlightModifier: Modifier,
    currentUid: String, partnerName: String, myBubbleColor: Color,
    onLongPress: (Message) -> Unit, onDownloadFile: (String, String) -> Unit,
    onScrollToReply: (String) -> Unit
) {
    var showFileMenu by remember(message.messageId) { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(4.dp)
            .then(highlightModifier)
            .background(
                color = if (isMine) myBubbleColor else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = {
                    if (!isPending && !message.mediaUrl.isNullOrBlank()) {
                        val mime = message.mimeType?.takeIf { it.isNotBlank() } ?: "*/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(message.mediaUrl), mime)
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "باز کردن با"))
                        } catch (e: ActivityNotFoundException) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(message.mediaUrl)
                                    )
                                )
                            }
                        }
                    }
                },
                onLongClick = { if (!isPending) onLongPress(message) }
            )
            .padding(10.dp)
            .widthIn(min = 200.dp, max = 280.dp)
    ) {
        val tint = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
        if (isPending) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = tint
            )
        } else {
            Icon(
                Icons.Filled.InsertDriveFile,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (!message.replyToMessageId.isNullOrBlank()) {
                ReplyQuoteBlock(
                    message = message,
                    isMine = isMine,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onClick = { onScrollToReply(message.replyToMessageId!!) }
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(message.fileName ?: "فایل", color = tint, maxLines = 1)
            Text(
                formatFileSize(message.fileSize ?: 0L),
                fontSize = 11.sp,
                color = tint.copy(alpha = 0.85f)
            )
            if (message.text.isNotBlank()) {
                Text(
                    message.text,
                    fontSize = 12.sp,
                    color = tint,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (!isPending) {
            val tint2 = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
            Box {
                IconButton(onClick = { showFileMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "منو", tint = tint2)
                }
                DropdownMenu(expanded = showFileMenu, onDismissRequest = { showFileMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("دانلود فایل") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = {
                            showFileMenu = false
                            if (!message.mediaUrl.isNullOrBlank()) {
                                onDownloadFile(message.mediaUrl, message.fileName ?: "file")
                            }
                        }
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoMessageBubble(
    message: Message, isMine: Boolean, isPending: Boolean, highlightModifier: Modifier,
    currentUid: String, partnerName: String, myBubbleColor: Color,
    onVideoClick: (String) -> Unit, onDownloadFile: (String, String) -> Unit,
    onLongPress: (Message) -> Unit,
    onScrollToReply: (String) -> Unit
) {
    var showVideoMenu by remember(message.messageId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(4.dp)
            .then(highlightModifier)
            .background(
                color = if (isMine) myBubbleColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        if (!message.replyToMessageId.isNullOrBlank()) {
            ReplyQuoteBlock(
                message = message, isMine = isMine, currentUid = currentUid, partnerName = partnerName,
                myBubbleColor = myBubbleColor, onClick = { onScrollToReply(message.replyToMessageId!!) }
            )
            Spacer(Modifier.height(4.dp))
        }

        if (!message.mediaUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = { onVideoClick(message.mediaUrl) },
                    onLongClick = { if (!isPending) onLongPress(message) }
                )
            ) {
                VideoThumbnail(
                    url = message.mediaUrl,
                    durationMs = message.durationMs ?: 0L
                )
                if (!isPending) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        IconButton(
                            onClick = { showVideoMenu = true },
                            modifier = Modifier.size(28.dp)
                                .background(Color.Black.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "منو", tint = Color.White)
                        }
                        DropdownMenu(expanded = showVideoMenu, onDismissRequest = { showVideoMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("دانلود ویدیو") },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                onClick = {
                                    showVideoMenu = false
                                    onDownloadFile(message.mediaUrl, "video_${message.messageId}.mp4")
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.size(width = 220.dp, height = 220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        if (message.text.isNotBlank()) {
            Text(text = message.text, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WallpaperProposalBubble(
    message: Message, isMine: Boolean, highlightModifier: Modifier,
    currentUid: String, partnerName: String, myBubbleColor: Color,
    onScrollToReply: (String) -> Unit, onRespond: (Message, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .then(highlightModifier)
            .background(
                color = if (isMine) myBubbleColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .widthIn(min = 220.dp, max = 250.dp)
            .padding(10.dp)
    ) {
        if (!message.mediaUrl.isNullOrBlank()) {
            if (!message.replyToMessageId.isNullOrBlank()) {
                ReplyQuoteBlock(
                    message = message,
                    isMine = isMine,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onClick = { onScrollToReply(message.replyToMessageId!!) }
                )
                Spacer(Modifier.height(4.dp))
            }
            coil.compose.AsyncImage(
                model = message.mediaUrl,
                contentDescription = "پیشنهاد پس‌زمینه",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = if (isMine) "پیشنهاد پس‌زمینه ارسال شد"
            else "$partnerName یک پس‌زمینه جدید برای این چت پیشنهاد داد",
            fontSize = 12.sp
        )
        when {
            !isMine && message.proposalStatus == ProposalStatus.PENDING -> {
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { onRespond(message, true) }, modifier = Modifier.weight(1f)) {
                        Text("قبول", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onRespond(message, false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("رد", fontSize = 12.sp)
                    }
                }
            }

            isMine && message.proposalStatus == ProposalStatus.PENDING ->
                Text(
                    "در انتظار پاسخ...",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            message.proposalStatus == ProposalStatus.ACCEPTED ->
                Text("پذیرفته شد ✓", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageMessageBubble(
    message: Message, isMine: Boolean, isPending: Boolean, highlightModifier: Modifier,
    currentUid: String, partnerName: String, myBubbleColor: Color,
    onLongPress: (Message) -> Unit, onImageClick: (String) -> Unit, onSaveImage: (String) -> Unit,
    onScrollToReply: (String) -> Unit
) {
    var showImageMenu by remember(message.messageId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(4.dp)
            .then(highlightModifier)
            .background(
                color = if (isMine) myBubbleColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = { if (!message.mediaUrl.isNullOrBlank()) onImageClick(message.mediaUrl) },
                onLongClick = { if (!isPending) onLongPress(message) }
            )
    ) {
        if (!message.mediaUrl.isNullOrBlank()) {
            Box {
                ChatBubbleImage(url = message.mediaUrl)
                if (!isPending) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { showImageMenu = true },
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.35f),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "منو",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showImageMenu,
                            onDismissRequest = { showImageMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("ذخیره در گالری") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null
                                    )
                                },
                                onClick = { showImageMenu = false; onSaveImage(message.mediaUrl) }
                            )
                        }
                    }
                }
            }
        } else {
            if (!message.replyToMessageId.isNullOrBlank()) {
                ReplyQuoteBlock(
                    message = message,
                    isMine = isMine,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onClick = { onScrollToReply(message.replyToMessageId!!) }
                )
                Spacer(Modifier.height(4.dp))
            }
            Box(
                modifier = Modifier.size(width = 220.dp, height = 220.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (message.isEdited) {
            Text(
                text = "ویرایش شده", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioMessageBubble(
    message: Message, isMine: Boolean, isPending: Boolean, highlightModifier: Modifier,
    currentUid: String, partnerName: String, myBubbleColor: Color,
    audioPlaybackState: fit.vcare.apps.data.audio.AudioPlaybackState,
    onLongPress: (Message) -> Unit, onToggleAudio: (Message) -> Unit,
    onScrollToReply: (String) -> Unit
) {
    val isThisPlaying = audioPlaybackState.playingMessageId == message.messageId
    val progress = if (isThisPlaying && audioPlaybackState.durationMs > 0) {
        (audioPlaybackState.positionMs.toFloat() / audioPlaybackState.durationMs.toFloat()).coerceIn(
            0f,
            1f
        )
    } else 0f
    val displayTimeMs =
        if (isThisPlaying) audioPlaybackState.positionMs else (message.durationMs ?: 0L)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(4.dp)
            .then(highlightModifier)
            .background(
                color = if (isMine) myBubbleColor else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = { if (!isPending) onToggleAudio(message) },
                onLongClick = { if (!isPending) onLongPress(message) }
            )
            .padding(horizontal = 10.dp, vertical = 14.dp)
            .widthIn(min = 160.dp, max = 220.dp)
    ) {
        val iconTint = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
        if (isPending) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = iconTint
            )
        } else {
            Icon(
                imageVector = if (isThisPlaying && audioPlaybackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play", tint = iconTint, modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            val isLoaded = isThisPlaying
            var dragValue by remember(message.messageId) { mutableStateOf<Float?>(null) }
            val sliderValue = dragValue ?: progress

            if (!message.replyToMessageId.isNullOrBlank()) {
                ReplyQuoteBlock(
                    message = message,
                    isMine = isMine,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onClick = { onScrollToReply(message.replyToMessageId!!) }
                )
                Spacer(Modifier.height(4.dp))
            }

            Slider(
                value = sliderValue,
                onValueChange = { v ->
                    if (isLoaded) {
                        dragValue = v
                        AudioPlayerManager.seekTo((v * audioPlaybackState.durationMs).toLong())
                    }
                },
                onValueChangeFinished = { dragValue = null },
                enabled = isLoaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = iconTint, activeTrackColor = iconTint,
                    inactiveTrackColor = iconTint.copy(alpha = 0.3f),
                    disabledThumbColor = iconTint, disabledActiveTrackColor = iconTint,
                    disabledInactiveTrackColor = iconTint.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatDuration(displayTimeMs),
                fontSize = 11.sp,
                color = iconTint.copy(alpha = 0.85f)
            )
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    fontSize = 12.sp,
                    color = iconTint,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextMessageBubble(
    message: Message, isMine: Boolean, isPending: Boolean, highlightModifier: Modifier,
    currentUid: String, partnerName: String, myBubbleColor: Color,
    onLongPress: (Message) -> Unit, onScrollToReply: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .then(highlightModifier)
            .widthIn(max = 260.dp)
            .background(
                color = if (isMine) myBubbleColor else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(onClick = {}, onLongClick = { if (!isPending) onLongPress(message) })
            .padding(10.dp)
    ) {
        Column {
            if (!message.replyToMessageId.isNullOrBlank()) {
                ReplyQuoteBlock(
                    message = message,
                    isMine = isMine,
                    currentUid = currentUid,
                    partnerName = partnerName,
                    myBubbleColor = myBubbleColor,
                    onClick = { onScrollToReply(message.replyToMessageId!!) }
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = message.text,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
            )
            if (message.isEdited) {
                Text(
                    text = "ویرایش شده", fontSize = 10.sp,
                    color = (if (isMine) Color.White else MaterialTheme.colorScheme.onSurface).copy(
                        alpha = 0.6f
                    )
                )
            }
        }
    }
}