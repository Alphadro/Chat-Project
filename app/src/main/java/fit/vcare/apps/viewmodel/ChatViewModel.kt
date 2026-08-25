package fit.vcare.apps.viewmodel

import org.json.JSONArray
import android.app.Application
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.ChatScreenTracker
import fit.vcare.apps.data.audio.AudioPlayerManager
import fit.vcare.apps.data.audio.AudioRecorder
import fit.vcare.apps.data.audio.RecordedAudio
import fit.vcare.apps.data.local.ChatAppearancePrefs
import fit.vcare.apps.data.remote.MediaRepositoryImpl
import fit.vcare.apps.data.remote.ServerTimeSync
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import fit.vcare.apps.data.repository.getMyUidOrEmpty
import fit.vcare.apps.domain.model.ChatThemePresets
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.PartnerPresence
import fit.vcare.apps.domain.model.ProposalStatus
import fit.vcare.apps.domain.model.Relationship
import fit.vcare.apps.domain.model.RelationshipStatus
import fit.vcare.apps.domain.model.ReplyInfo
import fit.vcare.apps.fcm.ChatNotificationPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
//ChatViewModel.kt
private const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
private const val MAX_RECORDING_DURATION_MS = 120_000L
private const val MIN_RECORDING_DURATION_MS = 700L
private const val MAX_MESSAGE_CHARS = 1000

data class ChatUiState(
    val isLoading: Boolean = false,
    val messages: List<Message> = emptyList(),
    val partnerName: String = "",
    val currentUid: String = "",
    val partnerUid: String = "",
    val partnerPhotoUrl: String? = null,
    val isSending: Boolean = false,
    val isUploadingImage: Boolean = false,
    val editingMessageId: String? = null,
    val partnerPresence: PartnerPresence? = null,
    val partnerIsTyping: Boolean = false,
    val partnerLastReadAt: Long? = null,
    val backgroundUri: String? = null,
    val themeKey: String = ChatThemePresets.default.key,
    // Voice Message
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val isUploadingAudio: Boolean = false,
    val recordingError: String? = null,
    val error: String? = null,
    val isUploadingBackground: Boolean = false,
    val pendingRecordedAudio: RecordedAudio? = null,
    val isClearingHistory: Boolean = false,
    val isDeletingChat: Boolean = false,
    val chatDeleted: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val hasMoreOlderMessages: Boolean = true,
    val relationship: Relationship? = null,
    val isMuted: Boolean = false,
    val isUploadingFile: Boolean = false,
    val pendingFile: PendingFileAttachment? = null,
    val replyingToMessage: Message? = null,
    val isUploadingVideo: Boolean = false,
    val pendingVideo: PendingVideoAttachment? = null,
)

data class PendingVideoAttachment(
    val uri: Uri,
    val mimeType: String,
    val fileSize: Long,
    val durationMs: Long
)

data class PendingFileAttachment(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private var conversationId: String? = null
    private var isChatScreenActive = false
    private val MAX_VIDEO_SIZE_BYTES = 100L * 1024 * 1024 // 100MB - Assumption

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _serverMessages = MutableStateFlow<List<Message>>(emptyList())
    private val _pendingMessages = MutableStateFlow<List<Message>>(emptyList())

    private val maxImageDimensionPx = 1280
    private val jpegQuality = 70

    private var lastWrittenReadAt: Long = 0L
    private var heartbeatJob: Job? = null

    private var typingActive = false
    private var stopTypingJob: Job? = null

    private val audioRecorder = AudioRecorder(context)
    private var recordingTimerJob: Job? = null

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (isChatScreenActive) resumePresence()
        }

        override fun onStop(owner: LifecycleOwner) {
            pausePresence()
            // اگر اپ رفت پس‌زمینه درحالی‌که ضبط فعال بود، ایمن لغو کن (فایل ارسال نشود)
            if (_uiState.value.isRecording) {
                cancelRecording()
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
    }

    fun downloadFile(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "دانلود شروع شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "دانلود ناموفق بود", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveImageToGallery(url: String) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { java.net.URL(url).openStream().use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                Toast.makeText(context, "دانلود عکس ناموفق بود", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val fileName = "VCare_${System.currentTimeMillis()}.jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + "/VCare"
                            )
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@runCatching false
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                    true
                }.getOrDefault(false)
            }

            Toast.makeText(
                context,
                if (saved) "عکس در گالری ذخیره شد" else "ذخیره عکس ناموفق بود",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun startReply(message: Message) {
        if (message.status == MessageStatus.PENDING) return
        _uiState.value = _uiState.value.copy(replyingToMessage = message, editingMessageId = null)
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingToMessage = null)
    }

    private fun replyPreviewTextFor(message: Message): String = when (message.type) {
        MessageType.IMAGE -> if (message.text.isNotBlank()) message.text else "📷 عکس"
        MessageType.AUDIO -> "🎤 پیام صوتی"
        MessageType.FILE -> message.fileName ?: "📎 فایل"
        MessageType.WALLPAPER_PROPOSAL -> "🖼️ پیشنهاد پس‌زمینه"
        else -> message.text
    }

    private fun buildReplyInfo(): ReplyInfo? {
        val msg = _uiState.value.replyingToMessage ?: return null
        return ReplyInfo(msg.messageId, msg.senderId, replyPreviewTextFor(msg), msg.type)
    }

    fun onFilePicked(uri: Uri) {
        val (name, size) = queryFileMeta(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        if (size > MAX_FILE_SIZE_BYTES) {
            _uiState.value = _uiState.value.copy(error = "حجم فایل بیشتر از حد مجاز است")
            return
        }

        _uiState.value = _uiState.value.copy(
            pendingFile = PendingFileAttachment(uri, name, size, mimeType)
        )
    }

    fun cancelPendingFile() {
        _uiState.value = _uiState.value.copy(pendingFile = null)
    }

    /** ارسال نهایی فایل انتخاب‌شده به همراه کپشن اختیاری */
    fun sendPendingFile(caption: String = "") {
        val pending = _uiState.value.pendingFile ?: return
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)

        _uiState.value = _uiState.value.copy(pendingFile = null)

        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val trimmedCaption = caption.trim()

        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = trimmedCaption,
            type = MessageType.FILE,
            mediaUrl = pending.uri.toString(),   // پیش‌نمایش موقت محلی
            createdAt = now,
            status = MessageStatus.PENDING,
            mimeType = pending.mimeType,
            fileSize = pending.fileSize,
            fileName = pending.fileName
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingFile = true, error = null)

            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(pending.uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                _uiState.value =
                    _uiState.value.copy(isUploadingFile = false, error = "خواندن فایل ناموفق بود")
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                return@launch
            }

            MediaRepositoryImpl.uploadFile(context, bytes, pending.fileName, pending.mimeType)
                .onSuccess { url ->
                    ChatRepositoryImpl.sendFileMessage(
                        context = context, conversationId = convId, senderId = myUid,
                        mediaUrl = url, fileName = pending.fileName, fileSize = pending.fileSize,
                        mimeType = pending.mimeType, caption = trimmedCaption, messageId = tempId
                    ).onSuccess {
                        _uiState.value = _uiState.value.copy(isUploadingFile = false)
                        _pendingMessages.value = _pendingMessages.value.map {
                            if (it.messageId == tempId) it.copy(
                                status = MessageStatus.SENT,
                                mediaUrl = url
                            ) else it
                        }
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isUploadingFile = false,
                            error = e.message ?: "ارسال فایل ناموفق بود"
                        )
                        _pendingMessages.value =
                            _pendingMessages.value.filterNot { it.messageId == tempId }
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingFile = false,
                        error = e.message ?: "آپلود فایل ناموفق بود"
                    )
                    _pendingMessages.value =
                        _pendingMessages.value.filterNot { it.messageId == tempId }
                }
        }
    }

    private fun queryFileMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    fun loadOlderMessages() {
        val convId = conversationId ?: return
        if (_uiState.value.isLoadingOlderMessages || !_uiState.value.hasMoreOlderMessages) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOlderMessages = true)
            val hasMore = ChatRepositoryImpl.loadOlderMessages(context, convId).getOrDefault(false)
            _uiState.value =
                _uiState.value.copy(isLoadingOlderMessages = false, hasMoreOlderMessages = hasMore)
        }
    }


    fun startObserving(conversationId: String, partnerUid: String, partnerName: String) {
        this.conversationId = conversationId
        this.isChatScreenActive = true
        val myUid = getMyUidOrEmpty(context)
        lastWrittenReadAt = 0L
        _pendingMessages.value = emptyList()
        ChatScreenTracker.onChatOpened(conversationId)

        viewModelScope.launch { ServerTimeSync.ensureSynced(context) } // ← جدید


        val savedBackground = ChatAppearancePrefs.getEffectiveBackgroundUri(context, conversationId)
        val savedTheme = ChatAppearancePrefs.getEffectiveThemeKey(context, conversationId)

        _uiState.value = _uiState.value.copy(
            partnerName = partnerName,
            partnerUid = partnerUid,
            currentUid = myUid,
            isLoading = false,
            messages = emptyList(),
            backgroundUri = savedBackground,
            themeKey = savedTheme,
            isMuted = ChatNotificationPrefs.isMuted(context, conversationId)

        )
        viewModelScope.launch {
            PartnerRepositoryImpl.getUserBasicInfo(context, partnerUid)
                .onSuccess { info ->
                    _uiState.value = _uiState.value.copy(partnerPhotoUrl = info.photoUrl)
                }
        }
        val messagesFlow = ChatRepositoryImpl.observeMessages(
            viewModelScope,
            context,
            conversationId,
            myUid,
            3000L
        )
        viewModelScope.launch {
            messagesFlow.collectLatest { serverMsgs ->
                if (serverMsgs.isNotEmpty()) {
                    // فقط وقتی سرور چیزی برگردوند (یا از اول کشی نبود) وضعیت رو با نتیجه‌ی سرور جایگزین کن،
                    // تا یک پاسخ خالی موقت آفلاین، کش موجود رو پاک نکنه
                    _serverMessages.value = serverMsgs
                }
                val serverIds = serverMsgs.map { it.messageId }.toSet()
                _pendingMessages.value =
                    _pendingMessages.value.filterNot { it.messageId in serverIds }

                val latest = serverMsgs.lastOrNull()?.createdAt ?: 0L
                if (latest > lastWrittenReadAt) {
                    lastWrittenReadAt = latest
                    ChatRepositoryImpl.updateLastRead(context, conversationId, myUid, latest)
                    ChatRepositoryImpl.resetUnreadCount(context, conversationId, myUid) // ← جدید
                }
            }
        }

        viewModelScope.launch {
            combine(_serverMessages, _pendingMessages) { server, pending ->
                (server + pending).sortedBy { it.createdAt }
            }.collectLatest { merged ->
                _uiState.value = _uiState.value.copy(isLoading = false, messages = merged)
            }
        }

        val presenceFlow =
            PartnerRepositoryImpl.observeUserPresence(viewModelScope, context, partnerUid, 5000L)
        viewModelScope.launch {
            presenceFlow.collectLatest { presence ->
                _uiState.value = _uiState.value.copy(partnerPresence = presence)
            }
        }
        val relationshipFlow = PartnerRepositoryImpl.observeRelationshipStatus(
            viewModelScope,
            context,
            conversationId,
            5000L
        )
        viewModelScope.launch {
            relationshipFlow.collectLatest { relationship ->
                _uiState.value = _uiState.value.copy(relationship = relationship)
            }
        }
        val typingFlow = ChatRepositoryImpl.observePartnerTyping(
            viewModelScope,
            context,
            conversationId,
            partnerUid,
            2000L
        )
        viewModelScope.launch {
            typingFlow.collectLatest { isTyping ->
                _uiState.value = _uiState.value.copy(partnerIsTyping = isTyping)
            }
        }

        val readFlow = ChatRepositoryImpl.observePartnerReadState(
            viewModelScope,
            context,
            conversationId,
            partnerUid,
            3000L
        )
        viewModelScope.launch {
            readFlow.collectLatest { lastReadAt ->
                _uiState.value = _uiState.value.copy(partnerLastReadAt = lastReadAt)
            }
        }

        resumePresence()
    }

    fun stopObserving() {
        val convId = conversationId
        if (convId != null) {
            ChatScreenTracker.onChatClosed(convId)
            ChatRepositoryImpl.stopObservingMessages(convId)
            ChatRepositoryImpl.stopObservingTyping(convId)
            ChatRepositoryImpl.stopObservingPartnerReadState(convId)
        }
        val partnerUid = _uiState.value.partnerUid
        if (partnerUid.isNotBlank()) {
            PartnerRepositoryImpl.stopObservingPresence(partnerUid)
            if (convId != null) {
                PartnerRepositoryImpl.stopObservingRelationshipStatus(convId)
            }
        }
        isChatScreenActive = false
        pausePresence()
        clearTypingState()

        // اگر ضبط فعال بود، صفحه را ترک کردیم -> لغو ایمن، چیزی ارسال نشود
        if (_uiState.value.isRecording) {
            cancelRecording()
        }
        AudioPlayerManager.release()
    }

    private fun resumePresence() {
        if (!isChatScreenActive) return
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            var attempts = 0
            while (isActive && attempts < 3) {
                val result = PartnerRepositoryImpl.updatePresence(context, isOnline = true)
                if (result.isSuccess) break
                attempts++
                delay(1500L)
            }
            while (isActive) {
                delay(8000L)
                PartnerRepositoryImpl.updatePresence(context, isOnline = true)
            }
        }
    }

    private fun pausePresence() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        viewModelScope.launch {
            var attempts = 0
            while (attempts < 2) {
                val result = PartnerRepositoryImpl.updatePresence(context, isOnline = false)
                if (result.isSuccess) break
                attempts++
                delay(800L)
            }
        }
    }


    fun sendMessage(text: String) {
        val convId = conversationId ?: return
        if (text.isBlank()) return
        clearTypingState()
        val chunks = splitMessageIntoChunks(text.trim(), MAX_MESSAGE_CHARS)
        val replyInfo = buildReplyInfo()
        _uiState.value = _uiState.value.copy(replyingToMessage = null)

        viewModelScope.launch {
            chunks.forEachIndexed { index, chunk ->
                sendSingleMessageAwait(convId, chunk, replyTo = if (index == 0) replyInfo else null)
            }
        }
    }

    private suspend fun sendSingleMessageAwait(
        convId: String,
        text: String,
        replyTo: ReplyInfo? = null
    ) {
        val myUid = getMyUidOrEmpty(context)
        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val pendingMsg = Message(
            messageId = tempId, conversationId = convId, senderId = myUid,
            text = text, type = MessageType.TEXT, mediaUrl = null,
            createdAt = now, status = MessageStatus.PENDING,
            replyToMessageId = replyTo?.messageId, replyToSenderId = replyTo?.senderId,
            replyToText = replyTo?.text, replyToType = replyTo?.type
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg
        _uiState.value = _uiState.value.copy(isSending = true, error = null)

        ChatRepositoryImpl.sendMessage(
            context,
            convId,
            myUid,
            text,
            messageId = tempId,
            replyTo = replyTo
        )
            .onSuccess {
                _pendingMessages.value = _pendingMessages.value.map {
                    if (it.messageId == tempId) it.copy(status = MessageStatus.SENT) else it
                }
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "ارسال ناموفق بود")
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
            }
        _uiState.value = _uiState.value.copy(isSending = false)
    }

    /** متن طولانی رو مثل تلگرام به چند پیام جدا تقسیم می‌کنه — ترجیحاً سر مرز فاصله/خط جدید می‌بره تا کلمه قطع نشه */
    private fun splitMessageIntoChunks(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxChars) {
            var splitIndex = remaining.lastIndexOf(' ', maxChars)
            if (splitIndex <= 0) splitIndex = remaining.lastIndexOf('\n', maxChars)
            if (splitIndex <= 0) splitIndex =
                maxChars // اگه هیچ مرز مناسبی نبود، مجبوریم وسط کلمه ببریم

            chunks.add(remaining.substring(0, splitIndex).trimEnd())
            remaining = remaining.substring(splitIndex).trimStart()
        }
        if (remaining.isNotBlank()) chunks.add(remaining)
        return chunks
    }

    /** همون منطق قبلیِ sendMessage، فقط حالا برای یک تکه‌ی از پیش بریده‌شده */
    private fun sendSingleMessage(convId: String, text: String) {
        val myUid = getMyUidOrEmpty(context)
        clearTypingState()

        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = text,
            type = MessageType.TEXT,
            mediaUrl = null,
            createdAt = now,
            status = MessageStatus.PENDING
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            ChatRepositoryImpl.sendMessage(context, convId, myUid, text, messageId = tempId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _pendingMessages.value = _pendingMessages.value.map {
                        if (it.messageId == tempId) it.copy(status = MessageStatus.SENT) else it
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = e.message ?: "ارسال ناموفق بود"
                    )
                    _pendingMessages.value =
                        _pendingMessages.value.filterNot { it.messageId == tempId }
                }
        }
    }

    fun sendImage(uri: Uri, caption: String = "") {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)

        clearTypingState()

        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = caption.trim(),
            type = MessageType.IMAGE,
            mediaUrl = uri.toString(),
            createdAt = now,
            status = MessageStatus.PENDING
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingImage = true, error = null)

            val bytesResult = withContext(Dispatchers.IO) {
                runCatching { compressImage(uri) }
            }

            val bytes = bytesResult.getOrNull()
            if (bytes == null) {
                _uiState.value =
                    _uiState.value.copy(isUploadingImage = false, error = "خواندن تصویر ناموفق بود")
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                return@launch
            }

            MediaRepositoryImpl.uploadImage(context, bytes)
                .onSuccess { url ->
                    ChatRepositoryImpl.sendImageMessage(
                        context,
                        convId,
                        myUid,
                        url,
                        caption,
                        messageId = tempId
                    )
                        .onSuccess {
                            _uiState.value = _uiState.value.copy(isUploadingImage = false)
                            _pendingMessages.value = _pendingMessages.value.map {
                                if (it.messageId == tempId) it.copy(
                                    status = MessageStatus.SENT,
                                    mediaUrl = url
                                ) else it
                            }
                        }
                        .onFailure { e ->
                            _uiState.value = _uiState.value.copy(
                                isUploadingImage = false,
                                error = e.message ?: "ارسال تصویر ناموفق بود"
                            )
                            _pendingMessages.value =
                                _pendingMessages.value.filterNot { it.messageId == tempId }
                        }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingImage = false,
                        error = e.message ?: "آپلود تصویر ناموفق بود"
                    )
                    _pendingMessages.value =
                        _pendingMessages.value.filterNot { it.messageId == tempId }
                }
        }
    }

    // ───────────────────── Voice Message ─────────────────────

    fun startRecording() {
        if (_uiState.value.isRecording) return

        val started = audioRecorder.start()
        if (!started) {
            _uiState.value = _uiState.value.copy(
                recordingError = "میکروفون در دسترس نیست. لطفاً دوباره تلاش کنید."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isRecording = true,
            recordingDurationMs = 0L,
            recordingError = null
        )

        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive && _uiState.value.isRecording) {
                val elapsed = System.currentTimeMillis() - startedAt
                _uiState.value = _uiState.value.copy(recordingDurationMs = elapsed)
                if (elapsed >= MAX_RECORDING_DURATION_MS) {
                    stopRecordingAndSend()
                    break
                }
                delay(200L)
            }
        }
    }

    fun cancelRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        audioRecorder.cancel()
        _uiState.value = _uiState.value.copy(isRecording = false, recordingDurationMs = 0L)
    }

    fun stopRecordingAndSend() {
        if (!_uiState.value.isRecording) return
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val elapsedMs = _uiState.value.recordingDurationMs
        val recorded = audioRecorder.stop()
        _uiState.value = _uiState.value.copy(isRecording = false, recordingDurationMs = 0L)

        if (recorded == null) {
            _uiState.value =
                _uiState.value.copy(recordingError = "ضبط صدا خیلی کوتاه بود یا ناموفق بود")
            return
        }
        if (elapsedMs < MIN_RECORDING_DURATION_MS) {
            recorded.file.delete()
            _uiState.value =
                _uiState.value.copy(recordingError = "ضبط خیلی کوتاه بود، کمی بیشتر نگه دارید")
            return
        }

        val convId = conversationId ?: run { recorded.file.delete(); return }
        val myUid = getMyUidOrEmpty(context)

        // ← اینجا مهمه: بگیر و فوراً پاک کن
        val replyInfo = buildReplyInfo()
        _uiState.value = _uiState.value.copy(replyingToMessage = null)

        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val fileSize = recorded.file.length()

        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = "",
            type = MessageType.AUDIO,
            mediaUrl = recorded.file.absolutePath,
            createdAt = now,
            status = MessageStatus.PENDING,
            durationMs = recorded.durationMs,
            mimeType = recorded.mimeType,
            fileSize = fileSize,
            replyToMessageId = replyInfo?.messageId,
            replyToSenderId = replyInfo?.senderId,
            replyToText = replyInfo?.text,
            replyToType = replyInfo?.type
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAudio = true, error = null)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { recorded.file.readBytes() }.getOrNull()
            }
            if (bytes == null) {
                _uiState.value = _uiState.value.copy(
                    isUploadingAudio = false,
                    error = "خواندن فایل صوتی ناموفق بود"
                )
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                recorded.file.delete()
                return@launch
            }
            MediaRepositoryImpl.uploadAudio(context, bytes, recorded.mimeType)
                .onSuccess { url ->
                    ChatRepositoryImpl.sendAudioMessage(
                        context = context,
                        conversationId = convId,
                        senderId = myUid,
                        mediaUrl = url,
                        durationMs = recorded.durationMs,
                        mimeType = recorded.mimeType,
                        fileSize = fileSize,
                        messageId = tempId,
                        replyTo = replyInfo   // ← اینجا هم پاس بده
                    ).onSuccess {
                        _uiState.value = _uiState.value.copy(isUploadingAudio = false)
                        _pendingMessages.value = _pendingMessages.value.map {
                            if (it.messageId == tempId) it.copy(
                                status = MessageStatus.SENT,
                                mediaUrl = url
                            ) else it
                        }
                        recorded.file.delete()
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isUploadingAudio = false,
                            error = e.message ?: "ارسال پیام صوتی ناموفق بود"
                        )
                        _pendingMessages.value =
                            _pendingMessages.value.filterNot { it.messageId == tempId }
                        recorded.file.delete()
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingAudio = false,
                        error = e.message ?: "آپلود صدا ناموفق بود"
                    )
                    _pendingMessages.value =
                        _pendingMessages.value.filterNot { it.messageId == tempId }
                    recorded.file.delete()
                }
        }
    }


    /** ضبط رو متوقف می‌کنه و preview (با امکان کپشن) نشون می‌ده — هنوز چیزی آپلود/ارسال نمی‌شه */
    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val elapsedMs = _uiState.value.recordingDurationMs   // ← جدید
        val recorded = audioRecorder.stop()
        _uiState.value = _uiState.value.copy(isRecording = false, recordingDurationMs = 0L)

        if (recorded == null) {
            _uiState.value =
                _uiState.value.copy(recordingError = "ضبط صدا خیلی کوتاه بود یا ناموفق بود")
            return
        }

        if (elapsedMs < MIN_RECORDING_DURATION_MS) {           // ← جدید
            recorded.file.delete()
            _uiState.value =
                _uiState.value.copy(recordingError = "ضبط خیلی کوتاه بود، کمی بیشتر نگه دارید")
            return
        }

        _uiState.value = _uiState.value.copy(pendingRecordedAudio = recorded)
    }

    fun cancelPendingRecordedAudio() {
        _uiState.value.pendingRecordedAudio?.file?.delete()
        _uiState.value = _uiState.value.copy(pendingRecordedAudio = null)
    }

    /** ارسال نهایی فایل ضبط‌شده به همراه کپشن اختیاری */
    fun sendRecordedAudio(caption: String = "") {
        val recorded = _uiState.value.pendingRecordedAudio ?: return
        val convId = conversationId ?: run { recorded.file.delete(); return }
        val myUid = getMyUidOrEmpty(context)

        // ← اضافه شد: بگیر و فوراً پاک کن
        val replyInfo = buildReplyInfo()
        _uiState.value = _uiState.value.copy(pendingRecordedAudio = null, replyingToMessage = null)

        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val fileSize = recorded.file.length()
        val trimmedCaption = caption.trim()

        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = trimmedCaption,
            type = MessageType.AUDIO,
            mediaUrl = recorded.file.absolutePath,
            createdAt = now,
            status = MessageStatus.PENDING,
            durationMs = recorded.durationMs,
            mimeType = recorded.mimeType,
            fileSize = fileSize,
            // ← اضافه شد
            replyToMessageId = replyInfo?.messageId,
            replyToSenderId = replyInfo?.senderId,
            replyToText = replyInfo?.text,
            replyToType = replyInfo?.type
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAudio = true, error = null)

            val bytes = withContext(Dispatchers.IO) {
                runCatching { recorded.file.readBytes() }.getOrNull()
            }
            if (bytes == null) {
                _uiState.value = _uiState.value.copy(
                    isUploadingAudio = false,
                    error = "خواندن فایل صوتی ناموفق بود"
                )
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                recorded.file.delete()
                return@launch
            }

            MediaRepositoryImpl.uploadAudio(context, bytes, recorded.mimeType)
                .onSuccess { url ->
                    ChatRepositoryImpl.sendAudioMessage(
                        context = context,
                        conversationId = convId,
                        senderId = myUid,
                        mediaUrl = url,
                        durationMs = recorded.durationMs,
                        mimeType = recorded.mimeType,
                        fileSize = fileSize,
                        caption = trimmedCaption,
                        messageId = tempId,
                        replyTo = replyInfo   // ← اضافه شد
                    ).onSuccess {
                        _uiState.value = _uiState.value.copy(isUploadingAudio = false)
                        _pendingMessages.value = _pendingMessages.value.map {
                            if (it.messageId == tempId) it.copy(
                                status = MessageStatus.SENT,
                                mediaUrl = url
                            ) else it
                        }
                        recorded.file.delete()
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isUploadingAudio = false,
                            error = e.message ?: "ارسال پیام صوتی ناموفق بود"
                        )
                        _pendingMessages.value =
                            _pendingMessages.value.filterNot { it.messageId == tempId }
                        recorded.file.delete()
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingAudio = false,
                        error = e.message ?: "آپلود صدا ناموفق بود"
                    )
                    _pendingMessages.value =
                        _pendingMessages.value.filterNot { it.messageId == tempId }
                    recorded.file.delete()
                }
        }
    }


    fun clearRecordingError() {
        _uiState.value = _uiState.value.copy(recordingError = null)
    }

    fun togglePlayAudio(message: Message) {
        val url = message.mediaUrl ?: return
        AudioPlayerManager.playOrToggle(context, message.messageId, url)
    }

    // ───────────────────────────────────────────────────────

    fun startEditingMessage(message: Message) {
        if (message.status == MessageStatus.PENDING) return
        _uiState.value =
            _uiState.value.copy(editingMessageId = message.messageId, replyingToMessage = null)
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(editingMessageId = null)
    }

    fun submitEdit(newText: String) {
        val convId = conversationId ?: return
        val editingId = _uiState.value.editingMessageId ?: return
        if (newText.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            ChatRepositoryImpl.editMessage(context, convId, editingId, newText)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSending = false, editingMessageId = null)
                    ChatRepositoryImpl.refreshNow(context, convId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = e.message ?: "ویرایش ناموفق بود"
                    )
                }
        }
    }

    fun deleteMessage(message: Message) {
        if (message.status == MessageStatus.PENDING) return
        val convId = conversationId ?: return
        viewModelScope.launch {
            ChatRepositoryImpl.deleteMessage(context, convId, message.messageId)
                .onSuccess { ChatRepositoryImpl.refreshNow(context, convId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message ?: "حذف پیام ناموفق بود")
                }
        }
    }

    fun clearChatHistory() {
        val convId = conversationId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearingHistory = true, error = null)
            ChatRepositoryImpl.clearChatHistory(context, convId)
                .onSuccess {
                    _pendingMessages.value = emptyList()
                    _serverMessages.value = emptyList()
                    _uiState.value =
                        _uiState.value.copy(isClearingHistory = false, messages = emptyList())
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isClearingHistory = false,
                        error = e.message ?: "پاک کردن تاریخچه ناموفق بود"
                    )
                }
        }
    }

    fun deleteChat() {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingChat = true, error = null)
            ChatRepositoryImpl.deleteChatForMe(context, convId, myUid)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeletingChat = false, chatDeleted = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isDeletingChat = false,
                        error = e.message ?: "حذف چت ناموفق بود"
                    )
                }
        }
    }

    fun notifyTyping() {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)

        if (!typingActive) {
            typingActive = true
            viewModelScope.launch {
                ChatRepositoryImpl.setTypingState(
                    context,
                    convId,
                    myUid,
                    true
                )
            }
        }

        stopTypingJob?.cancel()
        stopTypingJob = viewModelScope.launch {
            delay(3000L)
            typingActive = false
            ChatRepositoryImpl.setTypingState(context, convId, myUid, false)
        }
    }

    private fun clearTypingState() {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)
        stopTypingJob?.cancel()
        if (typingActive) {
            typingActive = false
            viewModelScope.launch {
                ChatRepositoryImpl.setTypingState(
                    context,
                    convId,
                    myUid,
                    false
                )
            }
        }
    }

    fun setChatBackground(uri: String?) {
        val convId = conversationId ?: return

        if (uri.isNullOrBlank()) {
            ChatAppearancePrefs.setBackgroundUri(context, convId, null)
            _uiState.value = _uiState.value.copy(
                backgroundUri = ChatAppearancePrefs.getEffectiveBackgroundUri(context, convId)
            )
            return
        }

        val myUid = getMyUidOrEmpty(context)
        val parsedUri = Uri.parse(uri)

        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = "",
            type = MessageType.WALLPAPER_PROPOSAL,
            mediaUrl = uri, // پیش‌نمایش محلی موقت تا قبل از تأیید سرور
            createdAt = now,
            status = MessageStatus.PENDING,
            proposalStatus = ProposalStatus.PENDING
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)

            val bytes =
                withContext(Dispatchers.IO) { runCatching { compressImage(parsedUri) } }.getOrNull()
            if (bytes == null) {
                _uiState.value = _uiState.value.copy(error = "خواندن تصویر پس‌زمینه ناموفق بود")
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                return@launch
            }

            MediaRepositoryImpl.uploadImage(context, bytes)
                .onSuccess { url ->
                    // فوراً برای خودم اعمال می‌شه
                    ChatAppearancePrefs.setBackgroundUri(context, convId, url)
                    _uiState.value = _uiState.value.copy(backgroundUri = url)

                    ChatRepositoryImpl.sendWallpaperProposal(
                        context,
                        convId,
                        myUid,
                        url,
                        messageId = tempId
                    )
                        .onSuccess {
                            _pendingMessages.value = _pendingMessages.value.map {
                                if (it.messageId == tempId) it.copy(
                                    status = MessageStatus.SENT,
                                    mediaUrl = url
                                ) else it
                            }
                        }
                        .onFailure { e ->
                            _uiState.value = _uiState.value.copy(
                                error = e.message ?: "ارسال پیشنهاد به طرف مقابل ناموفق بود"
                            )
                            _pendingMessages.value =
                                _pendingMessages.value.filterNot { it.messageId == tempId }
                        }
                }
                .onFailure { e ->
                    _uiState.value =
                        _uiState.value.copy(error = e.message ?: "آپلود پس‌زمینه ناموفق بود")
                    _pendingMessages.value =
                        _pendingMessages.value.filterNot { it.messageId == tempId }
                }
        }
    }

    fun respondToWallpaperProposal(message: Message, accept: Boolean) {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)
        val bgUrl = message.mediaUrl

        viewModelScope.launch {
            if (accept && !bgUrl.isNullOrBlank()) {
                ChatAppearancePrefs.setBackgroundUri(context, convId, bgUrl)
                _uiState.value = _uiState.value.copy(backgroundUri = bgUrl)

                ChatRepositoryImpl.updateWallpaperProposalStatus(
                    context,
                    convId,
                    message.messageId,
                    ProposalStatus.ACCEPTED
                )

                val myName =
                    PartnerRepositoryImpl.getUserBasicInfo(context, myUid).getOrNull()?.displayName
                        ?: "کاربر"
                ChatRepositoryImpl.sendMessage(
                    context,
                    convId,
                    myUid,
                    "🖼️ $myName پیشنهاد پس‌زمینه را پذیرفت"
                )
            } else {
                // رد کردن -> پیام پیشنهاد حذف می‌شه
                ChatRepositoryImpl.deleteMessage(context, convId, message.messageId)
            }
            ChatRepositoryImpl.refreshNow(context, convId)
        }
    }

    fun setChatTheme(themeKey: String) {
        val convId = conversationId ?: return
        ChatAppearancePrefs.setThemeKey(context, convId, themeKey)
        _uiState.value = _uiState.value.copy(themeKey = themeKey)
    }

    private fun compressImage(uri: Uri): ByteArray {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("فایل قابل خواندن نیست")
        boundsStream.use { input -> BitmapFactory.decodeStream(input, null, boundsOptions) }

        val actualWidth = boundsOptions.outWidth
        val actualHeight = boundsOptions.outHeight
        if (actualWidth <= 0 || actualHeight <= 0) {
            throw IllegalStateException("فرمت تصویر پشتیبانی نمی‌شود")
        }

        val sampleSize = calculateInSampleSize(actualWidth, actualHeight, maxImageDimensionPx)

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("فایل قابل خواندن نیست")
        val sampled =
            decodeStream.use { input -> BitmapFactory.decodeStream(input, null, decodeOptions) }
                ?: throw IllegalStateException("خواندن تصویر ناموفق بود")

        val finalBitmap = scaleDown(sampled, maxImageDimensionPx)
        val output = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)

        if (finalBitmap !== sampled) sampled.recycle()
        finalBitmap.recycle()
        return output.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetMaxDimension: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        // تا وقتی هر دو بعد حداقل ۲ برابر بزرگ‌تر از هدف هستن، sampleSize رو دو برابر کن
        while (currentWidth / 2 >= targetMaxDimension || currentHeight / 2 >= targetMaxDimension) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        if (_uiState.value.isRecording) {
            audioRecorder.cancel()
        }
        audioRecorder.release()
        stopObserving()
    }

    fun blockPartner() {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)
        viewModelScope.launch {
            PartnerRepositoryImpl.updateRelationshipStatus(
                context,
                convId,
                RelationshipStatus.BLOCKED,
                blockedBy = myUid
            )
                .onFailure { e ->
                    _uiState.value =
                        _uiState.value.copy(error = e.message ?: "مسدود کردن ناموفق بود")
                }
        }
    }

    fun unblockPartner() {
        val convId = conversationId ?: return
        viewModelScope.launch {
            PartnerRepositoryImpl.updateRelationshipStatus(
                context,
                convId,
                RelationshipStatus.ACTIVE,
                blockedBy = null
            )
                .onFailure { e ->
                    _uiState.value =
                        _uiState.value.copy(error = e.message ?: "لغو مسدودیت ناموفق بود")
                }
        }
    }

//    fun unmatchPartner(onDone: () -> Unit) {
//        val convId = conversationId ?: return
//        viewModelScope.launch {
//            PartnerRepositoryImpl.updateRelationshipStatus(context, convId, RelationshipStatus.ENDED)
//                .onSuccess { onDone() }
//                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "پایان رابطه ناموفق بود") }
//        }
//    }
//
//    fun reportPartner(reason: String, onDone: () -> Unit) {
//        val convId = conversationId ?: return
//        val partnerUid = _uiState.value.partnerUid
//        viewModelScope.launch {
//            PartnerRepositoryImpl.reportPartner(context, convId, partnerUid, reason.trim())
//                .onSuccess { onDone() }
//                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "ارسال گزارش ناموفق بود") }
//        }
//    }

    fun toggleMute() {
        val convId = conversationId ?: return
        val newValue = !_uiState.value.isMuted
        ChatNotificationPrefs.setMuted(context, convId, newValue)
        _uiState.value = _uiState.value.copy(isMuted = newValue)
    }

    fun unmatchPartner(onDone: () -> Unit) {
        val convId = conversationId ?: return
        viewModelScope.launch {
            PartnerRepositoryImpl.updateRelationshipStatus(
                context,
                convId,
                RelationshipStatus.ENDED
            )
                .onSuccess { onDone() }
                .onFailure { e ->
                    _uiState.value =
                        _uiState.value.copy(error = e.message ?: "پایان رابطه ناموفق بود")
                }
        }
    }

    fun reportPartner(reason: String, onDone: () -> Unit) {
        val convId = conversationId ?: return
        val partnerUid = _uiState.value.partnerUid
        viewModelScope.launch {
            PartnerRepositoryImpl.reportPartner(context, convId, partnerUid, reason.trim())
                .onSuccess { onDone() }
                .onFailure { e ->
                    _uiState.value =
                        _uiState.value.copy(error = e.message ?: "ارسال گزارش ناموفق بود")
                }
        }
    }

    fun toggleReaction(message: Message, emoji: String) {
        val convId = conversationId ?: return
        if (message.status == MessageStatus.PENDING) return
        val myUid = getMyUidOrEmpty(context)
        viewModelScope.launch {
            ChatRepositoryImpl.toggleMessageReaction(
                context,
                convId,
                message.messageId,
                myUid,
                emoji
            )
                .onFailure { e ->
                    _uiState.value =
                        _uiState.value.copy(error = e.message ?: "ثبت ری‌اکشن ناموفق بود")
                }
        }
    }

    fun onVideoPicked(uri: Uri) {
        val (name, size) = queryFileMeta(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

        if (size > MAX_VIDEO_SIZE_BYTES) {
            _uiState.value = _uiState.value.copy(error = "حجم ویدیو بیشتر از حد مجاز است")
            return
        }

        val durationMs = runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)

        _uiState.value = _uiState.value.copy(
            pendingVideo = PendingVideoAttachment(uri, mimeType, size, durationMs)
        )
    }

    fun cancelPendingVideo() {
        _uiState.value = _uiState.value.copy(pendingVideo = null)
    }

    fun sendPendingVideo(caption: String = "") {
        val pending = _uiState.value.pendingVideo ?: return
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)

        val replyInfo = buildReplyInfo()
        _uiState.value = _uiState.value.copy(pendingVideo = null, replyingToMessage = null)

        val tempId = UUID.randomUUID().toString()
        val now = ServerTimeSync.now()
        val trimmedCaption = caption.trim()

        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = trimmedCaption,
            type = MessageType.VIDEO,
            mediaUrl = pending.uri.toString(),
            createdAt = now,
            status = MessageStatus.PENDING,
            durationMs = pending.durationMs,
            mimeType = pending.mimeType,
            fileSize = pending.fileSize,
            replyToMessageId = replyInfo?.messageId,
            replyToSenderId = replyInfo?.senderId,
            replyToText = replyInfo?.text,
            replyToType = replyInfo?.type
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingVideo = true, error = null)

            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(pending.uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                _uiState.value =
                    _uiState.value.copy(isUploadingVideo = false, error = "خواندن ویدیو ناموفق بود")
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                return@launch
            }

            MediaRepositoryImpl.uploadVideo(context, bytes, pending.mimeType)
                .onSuccess { url ->
                    ChatRepositoryImpl.sendVideoMessage(
                        context = context,
                        conversationId = convId,
                        senderId = myUid,
                        mediaUrl = url,
                        durationMs = pending.durationMs,
                        mimeType = pending.mimeType,
                        fileSize = pending.fileSize,
                        caption = trimmedCaption,
                        messageId = tempId,
                        replyTo = replyInfo
                    ).onSuccess {
                        _uiState.value = _uiState.value.copy(isUploadingVideo = false)
                        _pendingMessages.value = _pendingMessages.value.map {
                            if (it.messageId == tempId) it.copy(
                                status = MessageStatus.SENT,
                                mediaUrl = url
                            ) else it
                        }
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isUploadingVideo = false,
                            error = e.message ?: "ارسال ویدیو ناموفق بود"
                        )
                        _pendingMessages.value =
                            _pendingMessages.value.filterNot { it.messageId == tempId }
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingVideo = false,
                        error = e.message ?: "آپلود ویدیو ناموفق بود"
                    )
                    _pendingMessages.value =
                        _pendingMessages.value.filterNot { it.messageId == tempId }
                }
        }
    }
}