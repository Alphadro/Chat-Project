package fit.vcare.apps.ui.chat

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import fit.vcare.apps.data.audio.AudioPlayerManager
import fit.vcare.apps.domain.model.ChatThemeOption
import fit.vcare.apps.domain.model.ChatThemePresets
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.ProposalStatus
import fit.vcare.apps.viewmodel.ChatUiState
import fit.vcare.apps.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imePadding

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private const val PRESENCE_STALE_MS = 20_000L
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

private fun buildChatEntries(messages: List<Message>): List<ChatListEntry> {
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
private val IMAGE_MAX_WIDTH = 240.dp
private val IMAGE_MAX_HEIGHT = 320.dp
private val IMAGE_MIN_WIDTH = 120.dp
private val IMAGE_MIN_HEIGHT = 120.dp

/**
 * منطق شبیه تلگرام: عکس رو با حفظ نسبت ابعاد داخل یک باکس max×max جا می‌ده.
 * اگه نسبت خیلی افراطی باشه (خیلی دراز یا خیلی پهن)، یک ضلع روی min قفل می‌شه
 * و عکس با ContentScale.Crop از داخل باکس تنظیم می‌شه (بدون کش اومدن/تغییر شکل).
 */
private fun computeBubbleImageSize(
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
        // عکس خیلی دراز (پرتره) -> ارتفاع روی max قفل، عرض کوچیک‌تر می‌شه
        h = IMAGE_MAX_HEIGHT.value
        w = h * ratio
        return if (w < IMAGE_MIN_WIDTH.value) {
            Triple(IMAGE_MIN_WIDTH, IMAGE_MAX_HEIGHT, ContentScale.Crop)
        } else {
            Triple(w.dp, h.dp, ContentScale.Fit)
        }
    }

    // عکس خیلی پهن (لنداسکیپ کشیده) -> عرض روی max، ارتفاع خیلی کوچیک می‌شه
    h = IMAGE_MIN_HEIGHT.value
    w = h * ratio
    return if (w > IMAGE_MAX_WIDTH.value) {
        Triple(IMAGE_MAX_WIDTH, IMAGE_MIN_HEIGHT, ContentScale.Crop)
    } else {
        Triple(w.dp, h.dp, ContentScale.Fit)
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
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }
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
    LaunchedEffect(uiState.chatDeleted) {
        if (uiState.chatDeleted) {
            navController.popBackStack()
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
            listState.scrollToItem(0)
        }
    }

    val chatEntries = remember(uiState.messages) { buildChatEntries(uiState.messages) }
    val reversedEntries = remember(chatEntries) { chatEntries.asReversed() }

    val matchedMessageIds = remember(uiState.messages, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else uiState.messages.filter {
            it.type == MessageType.TEXT && it.text.contains(searchQuery, ignoreCase = true)
        }.map { it.messageId }
    }
    LaunchedEffect(matchedMessageIds) { currentMatchPointer = 0 }
    val highlightedMessageId = matchedMessageIds.getOrNull(currentMatchPointer)

    fun indexOfMessageInReversed(messageId: String): Int =
        reversedEntries.indexOfFirst { it is ChatListEntry.MessageEntry && it.message.messageId == messageId }
    Column(modifier = Modifier.fillMaxSize() .windowInsetsPadding(
        WindowInsets.navigationBars.union(WindowInsets.ime)
    )) {
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
                    if (matchedMessageIds.isNotEmpty()) {
                        Text(
                            text = "${currentMatchPointer + 1}/${matchedMessageIds.size}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(onClick = {
                            if (matchedMessageIds.isEmpty()) return@IconButton
                            currentMatchPointer = (currentMatchPointer - 1 + matchedMessageIds.size) % matchedMessageIds.size
                            val idx = indexOfMessageInReversed(matchedMessageIds[currentMatchPointer])
                            if (idx >= 0) coroutineScope.launch { listState.animateScrollToItem(idx) }
                        }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "قبلی")
                        }
                        IconButton(onClick = {
                            if (matchedMessageIds.isEmpty()) return@IconButton
                            currentMatchPointer = (currentMatchPointer + 1) % matchedMessageIds.size
                            val idx = indexOfMessageInReversed(matchedMessageIds[currentMatchPointer])
                            if (idx >= 0) coroutineScope.launch { listState.animateScrollToItem(idx) }
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
        }  else {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            navController.navigate(
                                "partner_profile/$partnerUid/${Uri.encode(partnerName)}"
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!uiState.partnerPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = uiState.partnerPhotoUrl,
                                contentDescription = "عکس پروفایل",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = partnerName.take(1).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

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
                            onClick = {
                                showOverflowMenu = false
                                showClearHistoryDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف چت") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showDeleteChatDialog = true
                            }
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
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    items(
                        items = reversedEntries,
                        key = { entry ->
                            when (entry) {
                                is ChatListEntry.MessageEntry -> entry.message.messageId
                                is ChatListEntry.DateHeaderEntry -> "date_${entry.dateKey}"
                            }
                        }
                    ) { entry ->
                        if (entry is ChatListEntry.DateHeaderEntry) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = entry.label,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            return@items
                        }

                        val message = (entry as ChatListEntry.MessageEntry).message
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
                                MessageType.WALLPAPER_PROPOSAL -> {
                                    Column(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .then(highlightModifier)
                                            .background(
                                                color = if (isMine) myBubbleColor.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .widthIn(min = 220.dp, max = 240.dp)
                                            .padding(10.dp)
                                    ) {
                                        if (!message.mediaUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = message.mediaUrl,
                                                contentDescription = "پیشنهاد پس‌زمینه",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp))
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
                                                    Button(
                                                        onClick = { viewModel.respondToWallpaperProposal(message, accept = true) },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("قبول", fontSize = 12.sp) }
                                                    Spacer(Modifier.width(8.dp))
                                                    OutlinedButton(
                                                        onClick = { viewModel.respondToWallpaperProposal(message, accept = false) },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("رد", fontSize = 12.sp) }
                                                }
                                            }
                                            isMine && message.proposalStatus == ProposalStatus.PENDING ->
                                                Text("در انتظار پاسخ...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            message.proposalStatus == ProposalStatus.ACCEPTED ->
                                                Text("پذیرفته شد ✓", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
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
                                            ChatBubbleImage(url = message.mediaUrl)
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
                                    // زمانی که در حال پخشه، موقعیت لحظه‌ای رو نشون بده؛ وگرنه کل مدت زمان
                                    val displayTimeMs = if (isThisPlaying) audioPlaybackState.positionMs
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
                                                imageVector = if (isThisPlaying && audioPlaybackState.isPlaying)
                                                    Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = "Play",
                                                tint = iconTint,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            val isLoaded = isThisPlaying // یعنی این ویس همونیه که الان توی پلیر لود شده (چه پلی چه پاز)
                                            var dragValue by remember(message.messageId) { mutableStateOf<Float?>(null) }
                                            val sliderValue = dragValue ?: progress

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
                                                modifier = Modifier.fillMaxWidth().height(20.dp),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = iconTint,
                                                    activeTrackColor = iconTint,
                                                    inactiveTrackColor = iconTint.copy(alpha = 0.3f),
                                                    disabledThumbColor = iconTint,
                                                    disabledActiveTrackColor = iconTint,
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

                    IconButton(onClick = { viewModel.stopRecording() }) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "توقف و پیش‌نمایش",
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
        val isWallpaperProposal = actionMessage.type == MessageType.WALLPAPER_PROPOSAL

        ModalBottomSheet(onDismissRequest = { actionsForMessage = null }) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {

                if (!isImage && !isAudio && !isWallpaperProposal) {
                    ListItem(
                        headlineContent = { Text("Copy") },
                        leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(actionMessage.text))
                            actionsForMessage = null
                        }
                    )

                    if (!isImage && !isAudio && !isWallpaperProposal) { ListItem(
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
                    )}
                }

                if (isMine) {
                    if (!isWallpaperProposal) {
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
// ── Preview قبل از ارسال پیام صوتی (کپشن) ──
    val pendingAudio = uiState.pendingRecordedAudio
    if (pendingAudio != null) {
        var audioCaption by remember(pendingAudio.file.absolutePath) { mutableStateOf("") }
        Dialog(onDismissRequest = { viewModel.cancelPendingRecordedAudio() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("پیام صوتی — ${formatDuration(pendingAudio.durationMs)}")
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = audioCaption,
                        onValueChange = { audioCaption = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("افزودن توضیح...") }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.cancelPendingRecordedAudio() }) {
                        Icon(Icons.Filled.Close, contentDescription = "لغو")
                    }
                    IconButton(onClick = { viewModel.sendRecordedAudio(audioCaption) }) {
                        Icon(Icons.Filled.Send, contentDescription = "ارسال")
                    }
                }
            }
        }
    }

    // ── نمایش تمام‌صفحه‌ی عکس با تپ ──────────────────────
    // ── نمایش تمام‌صفحه‌ی عکس با تپ ──────────────────────
    // ── نمایش تمام‌صفحه‌ی عکس با قابلیت زوم (پینچ + دابل‌تپ) ──
    val fsUrl = fullScreenImageUrl
    if (fsUrl != null) {
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember(fsUrl) { mutableStateOf(1f) }
            var offsetX by remember(fsUrl) { mutableStateOf(0f) }
            var offsetY by remember(fsUrl) { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = fsUrl,
                    contentDescription = "نمایش تمام‌صفحه تصویر",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(fsUrl) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale <= 1f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                        }
                        .pointerInput(fsUrl) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 3f
                                    }
                                }
                            )
                        }
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
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
    // ── دیالوگ تأیید پاک کردن تاریخچه ──────────────
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isClearingHistory) showClearHistoryDialog = false },
            title = { Text("پاک کردن تاریخچه") },
            text = { Text("آیا مطمئنید؟ تمام پیام‌های این چت پاک می‌شوند اما خود چت باقی می‌ماند.") },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isClearingHistory,
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("پاک کردن", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isClearingHistory,
                    onClick = { showClearHistoryDialog = false }
                ) {
                    Text("انصراف")
                }
            }
        )
    }

// ── دیالوگ تأیید حذف چت ──────────────
    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isDeletingChat) showDeleteChatDialog = false },
            title = { Text("حذف چت") },
            text = { Text("آیا مطمئنید؟ این چت به همراه تمام پیام‌ها برای شما حذف می‌شود.") },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDeletingChat,
                    onClick = {
                        viewModel.deleteChat()
                        showDeleteChatDialog = false
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isDeletingChat,
                    onClick = { showDeleteChatDialog = false }
                ) {
                    Text("انصراف")
                }
            }
        )
    }
    // ── لودینگ حین پاک کردن تاریخچه ──────────────
    if (uiState.isClearingHistory) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("در حال پاک کردن تاریخچه...")
            }
        }
    }

// ── لودینگ حین حذف چت ──────────────
    if (uiState.isDeletingChat) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("در حال حذف چت...")
            }
        }
    }
}

@Composable
private fun ChatBubbleImage(url: String) {
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
        if (size == null) Triple(IMAGE_MAX_WIDTH, 200.dp, ContentScale.Crop) // حالت لودینگ
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