package fit.vcare.apps.ui.chat

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import fit.vcare.apps.data.audio.AudioPlayerManager
import fit.vcare.apps.domain.model.ChatThemeOption
import fit.vcare.apps.domain.model.ChatThemePresets
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.viewmodel.ChatUiState
import fit.vcare.apps.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private const val PRESENCE_STALE_MS = 20_000L

private fun formatMessageTime(millis: Long): String =
    if (millis <= 0) "" else timeFormatter.format(Date(millis))

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun partnerStatusText(uiState: ChatUiState): String {
    if (uiState.partnerIsTyping) return "در حال تایپ..."
    val presence = uiState.partnerPresence ?: return ""
    val isFresh = presence.lastActiveAt > 0 &&
            (System.currentTimeMillis() - presence.lastActiveAt) < PRESENCE_STALE_MS
    return when {
        presence.isOnline && isFresh -> "آنلاین"
        presence.lastActiveAt > 0 -> "بازدید ${timeFormatter.format(Date(presence.lastActiveAt))}"
        else -> ""
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    conversationId: String,
    partnerUid: String,
    partnerName: String,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var actionsForMessage by remember { mutableStateOf<Message?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaption by remember { mutableStateOf("") }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showBackgroundSheet by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchPointer by remember { mutableStateOf(0) }

    // Voice message permission state
    var micPermissionRequestedOnce by remember { mutableStateOf(false) }
    var showMicPermanentlyDeniedDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = isSystemInDarkTheme()

    val activeTheme: ChatThemeOption = remember(uiState.themeKey) {
        ChatThemePresets.byKey(uiState.themeKey)
    }
    val myBubbleColor = if (isDarkTheme) activeTheme.darkColor else activeTheme.lightColor

    val audioPlaybackState by AudioPlayerManager.state.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            pendingCaption = ""
        }
    }

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setChatBackground(uri.toString())
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            if (micPermissionRequestedOnce) {
                // کاربر یک بار قبلاً هم رد کرده -> احتمالاً Permanently Denied
                showMicPermanentlyDeniedDialog = true
            }
        }
        micPermissionRequestedOnce = true
    }

    fun onMicClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startRecording()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.editingMessageId) {
        val editingId = uiState.editingMessageId
        if (editingId != null) {
            val original = uiState.messages.firstOrNull { it.messageId == editingId }
            messageText = original?.text ?: ""
        } else {
            messageText = ""
        }
    }

    DisposableEffect(lifecycleOwner, conversationId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startObserving(conversationId, partnerUid, partnerName)
                Lifecycle.Event.ON_STOP -> viewModel.stopObserving()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.startObserving(conversationId, partnerUid, partnerName)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopObserving()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (!isSearchActive && uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.lastIndex)
        }
    }

    val matchedIndices = remember(uiState.messages, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else uiState.messages.mapIndexedNotNull { index, msg ->
            if (msg.type != MessageType.AUDIO && msg.text.contains(searchQuery, ignoreCase = true)) index
            else null
        }
    }
    LaunchedEffect(matchedIndices) { currentMatchPointer = 0 }
    val highlightedMessageId = matchedIndices.getOrNull(currentMatchPointer)
        ?.let { uiState.messages.getOrNull(it)?.messageId }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSearchActive) {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("جستجو در پیام‌ها...") },
                        singleLine = true
                    )
                },
                actions = {
                    if (matchedIndices.isNotEmpty()) {
                        Text(
                            text = "${currentMatchPointer + 1}/${matchedIndices.size}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(onClick = {
                            if (matchedIndices.isEmpty()) return@IconButton
                            currentMatchPointer = (currentMatchPointer - 1 + matchedIndices.size) % matchedIndices.size
                            coroutineScope.launch { listState.animateScrollToItem(matchedIndices[currentMatchPointer]) }
                        }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "قبلی")
                        }
                        IconButton(onClick = {
                            if (matchedIndices.isEmpty()) return@IconButton
                            currentMatchPointer = (currentMatchPointer + 1) % matchedIndices.size
                            coroutineScope.launch { listState.animateScrollToItem(matchedIndices[currentMatchPointer]) }
                        }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "بعدی")
                        }
                    }
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن جستجو")
                    }
                }
            )
        } else {
            TopAppBar(
                title = {
                    Column {
                        Text(partnerName)
                        val statusText = partnerStatusText(uiState)
                        if (statusText.isNotBlank()) {
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                color = if (uiState.partnerIsTyping)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "منو")
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("پاک کردن تاریخچه") },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                            onClick = { showOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف چت") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { showOverflowMenu = false }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("جستجو در پیام‌ها") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                isSearchActive = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تغییر پس‌زمینه") },
                            leadingIcon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showBackgroundSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تم چت") },
                            leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showThemeSheet = true
                            }
                        )
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val bgUri = uiState.backgroundUri
            if (!bgUri.isNullOrBlank()) {
                AsyncImage(
                    model = bgUri,
                    contentDescription = "پس‌زمینه چت",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isDarkTheme) 0.35f else 0.15f))
                )
            }

            when {
                uiState.isLoading && uiState.messages.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.messages.isEmpty() ->
                    Text("هنوز پیامی ارسال نشده", Modifier.align(Alignment.Center))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(uiState.messages) { message ->
                        val isMine = message.senderId == uiState.currentUid
                        val isPending = message.status == MessageStatus.PENDING
                        val isHighlighted = message.messageId == highlightedMessageId

                        val highlightModifier = if (isHighlighted) {
                            Modifier.border(2.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp))
                        } else Modifier

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                        ) {
                            when (message.type) {
                                MessageType.IMAGE -> {
                                    Column(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .then(highlightModifier)
                                            .background(
                                                color = if (isMine) myBubbleColor.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (!message.mediaUrl.isNullOrBlank()) {
                                                        fullScreenImageUrl = message.mediaUrl
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isPending) actionsForMessage = message
                                                }
                                            )
                                    ) {
                                        if (!message.mediaUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = message.mediaUrl,
                                                contentDescription = "تصویر ارسالی",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(width = 220.dp, height = 220.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        } else {
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
                                                text = "ویرایش شده",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                    }
                                }

                                MessageType.AUDIO -> {
                                    val isThisPlaying = audioPlaybackState.playingMessageId == message.messageId
                                    val progress = if (isThisPlaying && audioPlaybackState.durationMs > 0) {
                                        (audioPlaybackState.positionMs.toFloat() / audioPlaybackState.durationMs.toFloat())
                                            .coerceIn(0f, 1f)
                                    } else 0f
                                    val displayDurationMs = if (isThisPlaying && audioPlaybackState.durationMs > 0)
                                        audioPlaybackState.durationMs
                                    else (message.durationMs ?: 0L)

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
                                                    if (!isPending) viewModel.togglePlayAudio(message)
                                                },
                                                onLongClick = {
                                                    if (!isPending) actionsForMessage = message
                                                }
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                                imageVector = if (isThisPlaying && audioPlaybackState.isPlaying)
                                                    Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = "Play",
                                                tint = iconTint,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = iconTint,
                                                trackColor = iconTint.copy(alpha = 0.3f)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = formatDuration(displayDurationMs),
                                                fontSize = 11.sp,
                                                color = iconTint.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .then(highlightModifier)
                                            .background(
                                                color = if (isMine) myBubbleColor
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    if (!isPending) actionsForMessage = message
                                                }
                                            )
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = message.text,
                                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (message.isEdited) {
                                                Text(
                                                    text = "ویرایش شده",
                                                    fontSize = 10.sp,
                                                    color = (if (isMine) Color.White else MaterialTheme.colorScheme.onSurface)
                                                        .copy(alpha = 0.6f)
                                                )
                                            }
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
                                            val partnerReadAt = uiState.partnerLastReadAt
                                            val isReadByPartner = partnerReadAt != null && partnerReadAt >= message.createdAt
                                            Icon(
                                                imageVector = if (isReadByPartner) Icons.Filled.DoneAll else Icons.Filled.Done,
                                                contentDescription = if (isReadByPartner) "خوانده شد" else "ارسال شد",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (isReadByPartner) myBubbleColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isUploadingImage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("در حال ارسال تصویر...")
                    }
                }
            }

            if (uiState.isUploadingAudio) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("در حال ارسال پیام صوتی...")
                    }
                }
            }
        }

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }
        uiState.recordingError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(3000L)
                viewModel.clearRecordingError()
            }
        }

        if (uiState.editingMessageId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("در حال ویرایش پیام", fontSize = 12.sp)
                TextButton(onClick = { viewModel.cancelEditing() }) {
                    Text("لغو")
                }
            }
        }

        if (!isSearchActive) {
            if (uiState.isRecording) {
                // ── نوار Recording: Timer + Cancel + Stop/Send ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.cancelRecording() }) {
                        Icon(Icons.Filled.Close, contentDescription = "لغو ضبط", tint = MaterialTheme.colorScheme.error)
                    }

                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatDuration(uiState.recordingDurationMs),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = { viewModel.stopRecordingAndSend() }) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "ارسال پیام صوتی",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = {
                            messageText = it
                            viewModel.notifyTyping()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp),
                        placeholder = { Text("Type a message...") }
                    )

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !uiState.isUploadingImage
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Add Image",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (messageText.isBlank() && uiState.editingMessageId == null) {
                        IconButton(
                            onClick = { onMicClick() },
                            enabled = !uiState.isUploadingAudio
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Record Voice Message",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    if (uiState.editingMessageId != null) {
                                        viewModel.submitEdit(messageText)
                                    } else {
                                        viewModel.sendMessage(messageText)
                                    }
                                    messageText = ""
                                }
                            },
                            enabled = !uiState.isSending
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // ── منوی کوتاه اکشن پیام ──────────────────────────────
    val actionMessage = actionsForMessage
    if (actionMessage != null) {
        val isMine = actionMessage.senderId == uiState.currentUid
        val isImage = actionMessage.type == MessageType.IMAGE
        val isAudio = actionMessage.type == MessageType.AUDIO

        ModalBottomSheet(onDismissRequest = { actionsForMessage = null }) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {

                if (!isImage && !isAudio) {
                    ListItem(
                        headlineContent = { Text("Copy") },
                        leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(actionMessage.text))
                            actionsForMessage = null
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Share") },
                        leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, actionMessage.text)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share"))
                            actionsForMessage = null
                        }
                    )
                }

                if (isMine) {
                    if (!isAudio) {
                        ListItem(
                            headlineContent = { Text(if (isImage) "Edit Caption" else "Edit") },
                            leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            modifier = Modifier.clickable {
                                viewModel.startEditingMessage(actionMessage)
                                actionsForMessage = null
                            }
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
                        modifier = Modifier.clickable {
                            if (audioPlaybackState.playingMessageId == actionMessage.messageId) {
                                AudioPlayerManager.stop()
                            }
                            viewModel.deleteMessage(actionMessage)
                            actionsForMessage = null
                        }
                    )
                }
            }
        }
    }

    // ── Preview قبل از ارسال عکس (عکس + کپشن) ──
    val pendingUri = pendingImageUri
    if (pendingUri != null) {
        Dialog(
            onDismissRequest = { pendingImageUri = null; pendingCaption = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { pendingImageUri = null; pendingCaption = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                AsyncImage(
                    model = pendingUri,
                    contentDescription = "پیش‌نمایش تصویر",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pendingCaption,
                        onValueChange = { pendingCaption = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp),
                        placeholder = { Text("افزودن توضیح...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendImage(pendingUri, pendingCaption)
                            pendingImageUri = null
                            pendingCaption = ""
                        }
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }

    // ── نمایش تمام‌صفحه‌ی عکس با تپ ──────────────────────
    val fsUrl = fullScreenImageUrl
    if (fsUrl != null) {
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullScreenImageUrl = null }
            ) {
                AsyncImage(
                    model = fsUrl,
                    contentDescription = "نمایش تمام‌صفحه تصویر",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // ── انتخاب پس‌زمینه چت ──────────────────────
    if (showBackgroundSheet) {
        ModalBottomSheet(onDismissRequest = { showBackgroundSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    "پس‌زمینه چت",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("انتخاب از گالری") },
                    leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showBackgroundSheet = false
                        backgroundPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                if (!uiState.backgroundUri.isNullOrBlank()) {
                    ListItem(
                        headlineContent = { Text("حذف پس‌زمینه") },
                        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.setChatBackground(null)
                            showBackgroundSheet = false
                        }
                    )
                }
            }
        }
    }

    // ── انتخاب تم رنگی چت ──────────────────────
    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 16.dp, start = 16.dp, end = 16.dp)) {
                Text(
                    "تم چت",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                ChatThemePresets.all.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowItems.forEach { option ->
                            val swatchColor = if (isDarkTheme) option.darkColor else option.lightColor
                            val isSelected = option.key == uiState.themeKey
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable {
                                        viewModel.setChatTheme(option.key)
                                        showThemeSheet = false
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .then(
                                            if (isSelected)
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            else Modifier
                                        )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(option.label, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── دیالوگ Permission رد شده به‌صورت دائم ──────────────
    if (showMicPermanentlyDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showMicPermanentlyDeniedDialog = false },
            title = { Text("دسترسی میکروفون لازم است") },
            text = { Text("برای ارسال پیام صوتی، لطفاً دسترسی میکروفون را از تنظیمات اپ فعال کنید.") },
            confirmButton = {
                TextButton(onClick = {
                    showMicPermanentlyDeniedDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("باز کردن تنظیمات")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicPermanentlyDeniedDialog = false }) {
                    Text("بعداً")
                }
            }
        )
    }
}