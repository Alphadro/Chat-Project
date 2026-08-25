package fit.vcare.apps.ui.chat
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fit.vcare.apps.data.audio.AudioPlayerManager
import fit.vcare.apps.domain.model.ChatThemeOption
import fit.vcare.apps.domain.model.ChatThemePresets
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import fit.vcare.apps.domain.model.RelationshipStatus

//ChatScreen
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

    val relationship = uiState.relationship
    val isBlockedByMe =
        relationship?.status == RelationshipStatus.BLOCKED && relationship.blockedBy == uiState.currentUid
    val isBlockedByPartner =
        relationship?.status == RelationshipStatus.BLOCKED && relationship.blockedBy == uiState.partnerUid

    val replyThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
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

    var showBlockDialog by remember { mutableStateOf(false) }
    var showUnmatchDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }

    val audioPlaybackState by AudioPlayerManager.state.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }

    var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }



    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.startsWith("video/")) {
                viewModel.onVideoPicked(uri)
            } else {
                pendingImageUri = uri
                pendingCaption = ""
            }
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onFilePicked(uri)
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
                Lifecycle.Event.ON_START -> viewModel.startObserving(
                    conversationId,
                    partnerUid,
                    partnerName
                )

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
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = reversedEntries.size
            totalItems > 0 && lastVisibleIndex >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.hasMoreOlderMessages, uiState.isLoadingOlderMessages) {
        if (shouldLoadMore && uiState.hasMoreOlderMessages && !uiState.isLoadingOlderMessages) {
            viewModel.loadOlderMessages()
        }
    }
    val matchedMessageIds = remember(uiState.messages, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else uiState.messages.filter {
            it.type == MessageType.TEXT && it.text.contains(searchQuery, ignoreCase = true)
        }.map { it.messageId }
    }
    LaunchedEffect(matchedMessageIds) { currentMatchPointer = 0 }
    val highlightedMessageId = matchedMessageIds.getOrNull(currentMatchPointer)

   Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime)
            )
    ) {
       val onPrevMatch: () -> Unit = {
           if (matchedMessageIds.isNotEmpty()) {
               currentMatchPointer = (currentMatchPointer - 1 + matchedMessageIds.size) % matchedMessageIds.size
               val idx = indexOfMessageInReversed(reversedEntries, matchedMessageIds[currentMatchPointer])
               if (idx >= 0) coroutineScope.launch { listState.animateScrollToItem(idx) }
           }
       }

       val onNextMatch: () -> Unit = {
           if (matchedMessageIds.isNotEmpty()) {
               currentMatchPointer = (currentMatchPointer + 1) % matchedMessageIds.size
               val idx = indexOfMessageInReversed(reversedEntries, matchedMessageIds[currentMatchPointer])
               if (idx >= 0) coroutineScope.launch { listState.animateScrollToItem(idx) }
           }
       }
        ChatTopBar(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            matchCount = matchedMessageIds.size,
            currentMatchIndex = currentMatchPointer,
            onPrevMatch = onPrevMatch,
            onNextMatch = onNextMatch,
            onCloseSearch = { isSearchActive = false; searchQuery = "" },
            partnerName = partnerName,
            partnerPhotoUrl = uiState.partnerPhotoUrl,
            statusText = partnerStatusText(uiState),
            isPartnerTyping = uiState.partnerIsTyping,
            onNavigateBack = { navController.popBackStack() },
            onOpenPartnerProfile = { navController.navigate("partner_profile/$partnerUid/${Uri.encode(partnerName)}") },
            showOverflowMenu = showOverflowMenu,
            onOverflowMenuToggle = { showOverflowMenu = it },
            isBlockedByMe = isBlockedByMe,
            isMuted = uiState.isMuted,
            onMenuAction = { action ->
                when (action) {
                    ChatMenuAction.ClearHistory -> showClearHistoryDialog = true
                    ChatMenuAction.DeleteChat -> showDeleteChatDialog = true
                    ChatMenuAction.Search -> isSearchActive = true
                    ChatMenuAction.ChangeBackground -> showBackgroundSheet = true
                    ChatMenuAction.ChangeTheme -> showThemeSheet = true
                    ChatMenuAction.Block -> showBlockDialog = true
                    ChatMenuAction.Unblock -> viewModel.unblockPartner()
                    ChatMenuAction.Report -> showReportDialog = true
                    ChatMenuAction.Unmatch -> showUnmatchDialog = true
                    ChatMenuAction.ToggleMute -> viewModel.toggleMute()
                }
            }
        )
       ChatMessageList(
           modifier = Modifier.weight(1f).fillMaxWidth(),
           uiState = uiState,
           reversedEntries = reversedEntries,
           listState = listState,
           highlightedMessageId = highlightedMessageId,
           myBubbleColor = myBubbleColor,
           isDarkTheme = isDarkTheme,
           coroutineScope = coroutineScope,
           replyThresholdPx = replyThresholdPx,
           partnerName = partnerName,
           onScrollToMessage = { /* داخل خودش استفاده می‌شه، لازم نیست اینجا کاری کنی */ },
           onReply = { viewModel.startReply(it) },
           onLongPress = { actionsForMessage = it },
           onImageClick = { fullScreenImageUrl = it },
           onVideoClick = { fullScreenVideoUrl = it },
           onToggleAudio = { viewModel.togglePlayAudio(it) },
           onDownloadFile = { url, name -> viewModel.downloadFile(url, name) },
           onSaveImage = { viewModel.saveImageToGallery(it) },
           onRespondToProposal = { msg, accept -> viewModel.respondToWallpaperProposal(msg, accept) }
       )
        ChatErrorBanner(uiState.error)
        ChatRecordingErrorBanner(uiState.recordingError) { viewModel.clearRecordingError() }
        if (uiState.replyingToMessage != null && uiState.editingMessageId == null) {
            ReplyPreviewBar(
                replyingToMessage = uiState.replyingToMessage!!,
                currentUid = uiState.currentUid,
                partnerName = partnerName,
                myBubbleColor = myBubbleColor,
                onCancelReply = { viewModel.cancelReply() }
            )
        }
        if (uiState.editingMessageId != null) {
            EditingPreviewBar(onCancelEditing = { viewModel.cancelEditing() })
        }

        if (!isSearchActive) {
            ChatBottomBar(
                isBlockedByMe = isBlockedByMe,
                isBlockedByPartner = isBlockedByPartner,
                partnerName = partnerName,
                onUnblock = { viewModel.unblockPartner() },
                isRecording = uiState.isRecording,
                recordingDurationMs = uiState.recordingDurationMs,
                onCancelRecording = { viewModel.cancelRecording() },
                onStopRecording = { viewModel.stopRecording() },
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                editingMessageId = uiState.editingMessageId,
                onNotifyTyping = { viewModel.notifyTyping() },
                onMicClick = { onMicClick() },
                onPickMedia = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                onSend = {
                    if (messageText.isNotBlank()) {
                        if (uiState.editingMessageId != null) viewModel.submitEdit(messageText)
                        else viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                isUploadingImage = uiState.isUploadingImage,
                isUploadingVideo = uiState.isUploadingVideo,
                isUploadingFile = uiState.isUploadingFile,
                isUploadingAudio = uiState.isUploadingAudio,
                isSending = uiState.isSending
            )
        }
    }

    // ── منوی کوتاه اکشن پیام ──────────────────────────────
    val actionMessage = actionsForMessage
    if (actionMessage != null) {
        MessageActionSheet(
            message = actionMessage,
            currentUid = uiState.currentUid,
            onDismiss = { actionsForMessage = null },
            onToggleReaction = { msg, emoji -> viewModel.toggleReaction(msg, emoji) },
            onReply = { viewModel.startReply(it) },
            onSaveImage = { viewModel.saveImageToGallery(it) },
            onDownloadFile = { url, name -> viewModel.downloadFile(url, name) },
            onCopyText = { clipboardManager.setText(AnnotatedString(it)) },
            onShareText = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, it)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share"))
            },
            onStartEditing = { viewModel.startEditingMessage(it) },
            onDelete = { msg ->
                if (audioPlaybackState.playingMessageId == msg.messageId) {
                    AudioPlayerManager.stop()
                }
                viewModel.deleteMessage(msg)
            }
        )
    }
    // ── Preview قبل از ارسال عکس (عکس + کپشن) ──
    val pendingVideo = uiState.pendingVideo
    if (pendingVideo != null) {
        PendingVideoPreviewDialog(
            videoUri = pendingVideo.uri,
            onDismiss = { viewModel.cancelPendingVideo() },
            onSend = { caption -> viewModel.sendPendingVideo(caption) }
        )
    }
    val pendingImgUri = pendingImageUri
    if (pendingImgUri != null) {
        PendingImagePreviewDialog(
            imageUri = pendingImgUri,
            onDismiss = { pendingImageUri = null; pendingCaption = "" },
            onSend = { caption ->
                viewModel.sendImage(pendingImgUri, caption)
                pendingImageUri = null
                pendingCaption = ""
            }
        )
    }
    val pendingFile = uiState.pendingFile
    if (pendingFile != null) {
        PendingFilePreviewDialog(
            fileName = pendingFile.fileName,
            fileSize = pendingFile.fileSize,
            onDismiss = { viewModel.cancelPendingFile() },
            onSend = { caption -> viewModel.sendPendingFile(caption) }
        )
    }

    val pendingAudio = uiState.pendingRecordedAudio
    if (pendingAudio != null) {
        PendingAudioPreviewDialog(
            durationMs = pendingAudio.durationMs,
            captionKey = pendingAudio.file.absolutePath,
            onDismiss = { viewModel.cancelPendingRecordedAudio() },
            onSend = { caption -> viewModel.sendRecordedAudio(caption) }
        )
    }

    // ── نمایش تمام‌صفحه‌ی عکس با تپ ──────────────────────
    // ── نمایش تمام‌صفحه‌ی عکس با تپ ──────────────────────
    // ── نمایش تمام‌صفحه‌ی عکس با قابلیت زوم (پینچ + دابل‌تپ) ──

    val fsVideoUrl = fullScreenVideoUrl
    if (fsVideoUrl != null) {
        FullscreenVideoDialog(
            videoUrl = fsVideoUrl,
            onDismiss = { fullScreenVideoUrl = null }
        )
    }

    val fsUrl = fullScreenImageUrl
    if (fsUrl != null) {
        FullscreenImageDialog(
            imageUrl = fsUrl,
            onDismiss = { fullScreenImageUrl = null }
        )
    }

    // ── انتخاب پس‌زمینه چت ──────────────────────
    if (showBackgroundSheet) {
        ChatBackgroundSheet(
            hasBackground = !uiState.backgroundUri.isNullOrBlank(),
            onDismiss = { showBackgroundSheet = false },
            onPickFromGallery = {
                backgroundPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveBackground = { viewModel.setChatBackground(null) }
        )
    }

    if (showThemeSheet) {
        ChatThemeSheet(
            currentThemeKey = uiState.themeKey,
            isDarkTheme = isDarkTheme,
            onDismiss = { showThemeSheet = false },
            onThemeSelected = { key -> viewModel.setChatTheme(key) }
        )
    }

    if (showMicPermanentlyDeniedDialog) {
        MicPermissionDeniedDialog(
            onDismiss = { showMicPermanentlyDeniedDialog = false },
            onOpenSettings = {
                showMicPermanentlyDeniedDialog = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }

    if (showClearHistoryDialog) {
        ClearHistoryConfirmDialog(
            isClearing = uiState.isClearingHistory,
            onDismiss = { showClearHistoryDialog = false },
            onConfirm = {
                viewModel.clearChatHistory()
                showClearHistoryDialog = false
            }
        )
    }

    if (showDeleteChatDialog) {
        DeleteChatConfirmDialog(
            isDeleting = uiState.isDeletingChat,
            onDismiss = { showDeleteChatDialog = false },
            onConfirm = {
                viewModel.deleteChat()
                showDeleteChatDialog = false
            }
        )
    }

    if (showBlockDialog) {
        BlockPartnerConfirmDialog(
            partnerName = partnerName,
            onDismiss = { showBlockDialog = false },
            onConfirm = {
                viewModel.blockPartner()
                showBlockDialog = false
            }
        )
    }

    if (showUnmatchDialog) {
        UnmatchConfirmDialog(
            onDismiss = { showUnmatchDialog = false },
            onConfirm = {
                viewModel.unmatchPartner { navController.popBackStack() }
                showUnmatchDialog = false
            }
        )
    }

    if (showReportDialog) {
        ReportPartnerDialog(
            reportReason = reportReason,
            onReasonChange = { reportReason = it },
            onDismiss = { showReportDialog = false; reportReason = "" },
            onConfirm = {
                viewModel.reportPartner(reportReason) {
                    Toast.makeText(context, "گزارش شما ثبت شد", Toast.LENGTH_SHORT).show()
                }
                showReportDialog = false
                reportReason = ""
            }
        )
    }

    if (uiState.isClearingHistory) {
        ChatLoadingOverlay("در حال پاک کردن تاریخچه...")
    }

    if (uiState.isDeletingChat) {
        ChatLoadingOverlay("در حال حذف چت...")
    }
} // ← اینجا کل تابع ChatScreen بسته می‌شه


