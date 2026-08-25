package fit.vcare.apps.ui.chat

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//ChatBubbleComponents.kt
@Composable
fun ReplyQuoteBlock(
    message: Message,
    isMine: Boolean,
    currentUid: String,
    partnerName: String,
    myBubbleColor: Color,
    onClick: () -> Unit
) {
    val senderLabel = if (message.replyToSenderId == currentUid) "شما" else partnerName
    val barColor = if (isMine) Color.White else myBubbleColor
    val bgColor = if (isMine) Color.White.copy(alpha = 0.16f) else myBubbleColor.copy(alpha = 0.10f)
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 30.dp)
                .background(barColor)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = senderLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor,
                maxLines = 1
            )
            Text(
                text = when (message.replyToType) {
                    MessageType.IMAGE -> if (!message.replyToText.isNullOrBlank()) message.replyToText!! else "📷 عکس"
                    MessageType.AUDIO -> "🎤 پیام صوتی"
                    MessageType.VIDEO -> "🎥 ویدیو"
                    MessageType.FILE -> message.replyToText?.takeIf { it.isNotBlank() } ?: "📎 فایل"
                    else -> message.replyToText ?: ""
                },
                fontSize = 12.sp,
                color = textColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChatBubbleImage(url: String) {
    var naturalSize by remember(url) { mutableStateOf<IntSize?>(null) }

    val painter = rememberAsyncImagePainter(
        model = url,
        onState = { state ->
            if (state is AsyncImagePainter.State.Success) {
                val d = state.result.drawable
                if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                    naturalSize = IntSize(d.intrinsicWidth, d.intrinsicHeight)
                }
            }
        }
    )

    val (boxWidth, boxHeight, scale) = remember(naturalSize) {
        val size = naturalSize
        if (size == null) Triple(IMAGE_MAX_WIDTH, 200.dp, ContentScale.Crop)
        else computeBubbleImageSize(size.width, size.height)
    }

    Box(
        modifier = Modifier
            .size(width = boxWidth, height = boxHeight)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painter,
            contentDescription = "تصویر ارسالی",
            contentScale = scale,
            modifier = Modifier.fillMaxSize()
        )
        if (naturalSize == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}
@Composable
fun VideoThumbnail(url: String, durationMs: Long) {
    var thumbnail by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(url) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching {
                android.media.MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, Uri.parse(url))
                    retriever.getFrameAtTime(0)
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(width = IMAGE_MAX_WIDTH, height = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        thumbnail?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "پیش‌نمایش ویدیو",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "پخش",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        if (durationMs > 0) {
            Text(
                text = formatDuration(durationMs),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun VideoPreviewPlayer(uri: Uri, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = {
                androidx.media3.ui.PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}