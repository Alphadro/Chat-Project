package fit.vcare.apps.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.viewmodel.ChatUiState
import fit.vcare.apps.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private const val ONLINE_THRESHOLD_MS = 40_000L

private fun formatMessageTime(millis: Long): String =
    if (millis <= 0) "" else timeFormatter.format(Date(millis))

private fun partnerStatusText(uiState: ChatUiState): String {
    if (uiState.partnerIsTyping) return "در حال تایپ..."
    val lastSeen = uiState.partnerLastSeen
    if (lastSeen != null && lastSeen > 0) {
        val diff = System.currentTimeMillis() - lastSeen
        return if (diff in 0..ONLINE_THRESHOLD_MS) "آنلاین"
        else "بازدید ${timeFormatter.format(Date(lastSeen))}"
    }
    return ""
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

    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            pendingCaption = ""
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
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading && uiState.messages.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.messages.isEmpty() ->
                    Text("هنوز پیامی ارسال نشده", Modifier.align(Alignment.Center))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(uiState.messages) { message ->
                        val isMine = message.senderId == uiState.currentUid
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                        ) {
                            when (message.type) {
                                MessageType.IMAGE -> {
                                    Column(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .background(
                                                color = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (!message.mediaUrl.isNullOrBlank()) {
                                                        fullScreenImageUrl = message.mediaUrl
                                                    }
                                                },
                                                onLongClick = { actionsForMessage = message }
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
                                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
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
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .background(
                                                color = if (isMine) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { actionsForMessage = message }
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

                            // ساعت + تیک وضعیت خواندن (فقط برای پیام‌های خودم)
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
                                    val partnerReadAt = uiState.partnerLastReadAt
                                    val isReadByPartner = partnerReadAt != null && partnerReadAt >= message.createdAt
                                    Icon(
                                        imageVector = if (isReadByPartner) Icons.Filled.DoneAll else Icons.Filled.Done,
                                        contentDescription = if (isReadByPartner) "خوانده شد" else "ارسال شد",
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isReadByPartner) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
        }

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
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

    // ── منوی کوتاه اکشن پیام ──────────────────────────────
    val actionMessage = actionsForMessage
    if (actionMessage != null) {
        val isMine = actionMessage.senderId == uiState.currentUid
        val isImage = actionMessage.type == MessageType.IMAGE

        ModalBottomSheet(onDismissRequest = { actionsForMessage = null }) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {

                if (!isImage) {
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
                    ListItem(
                        headlineContent = { Text(if (isImage) "Edit Caption" else "Edit") },
                        leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.startEditingMessage(actionMessage)
                            actionsForMessage = null
                        }
                    )
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
                            viewModel.deleteMessage(actionMessage)
                            actionsForMessage = null
                        }
                    )
                }
            }
        }
    }

    // ── Preview قبل از ارسال عکس (مثل تلگرام: عکس + کپشن) ──
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
}