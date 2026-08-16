package fit.vcare.apps.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.data.audio.AudioPlayerManager
import fit.vcare.apps.data.audio.AudioRecorder
import fit.vcare.apps.data.local.ChatAppearancePrefs
import fit.vcare.apps.data.remote.MediaRepositoryImpl
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import fit.vcare.apps.data.repository.getMyUidOrEmpty
import fit.vcare.apps.domain.model.ChatThemePresets
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.domain.model.MessageStatus
import fit.vcare.apps.domain.model.MessageType
import fit.vcare.apps.domain.model.PartnerPresence
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

private const val MAX_RECORDING_DURATION_MS = 60_000L

data class ChatUiState(
    val isLoading: Boolean = false,
    val messages: List<Message> = emptyList(),
    val partnerName: String = "",
    val currentUid: String = "",
    val partnerUid: String = "",
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
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private var conversationId: String? = null
    private var isChatScreenActive = false

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

    fun startObserving(conversationId: String, partnerUid: String, partnerName: String) {
        this.conversationId = conversationId
        this.isChatScreenActive = true
        val myUid = getMyUidOrEmpty(context)
        lastWrittenReadAt = 0L
        _serverMessages.value = emptyList()
        _pendingMessages.value = emptyList()

        val savedBackground = ChatAppearancePrefs.getBackgroundUri(context, conversationId)
        val savedTheme = ChatAppearancePrefs.getThemeKey(context, conversationId)

        _uiState.value = _uiState.value.copy(
            partnerName = partnerName,
            partnerUid = partnerUid,
            currentUid = myUid,
            isLoading = true,
            backgroundUri = savedBackground,
            themeKey = savedTheme
        )

        val messagesFlow = ChatRepositoryImpl.observeMessages(viewModelScope, context, conversationId, 3000L)
        viewModelScope.launch {
            messagesFlow.collectLatest { serverMsgs ->
                _serverMessages.value = serverMsgs
                val serverIds = serverMsgs.map { it.messageId }.toSet()
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId in serverIds }

                val latest = serverMsgs.lastOrNull()?.createdAt ?: 0L
                if (latest > lastWrittenReadAt) {
                    lastWrittenReadAt = latest
                    ChatRepositoryImpl.updateLastRead(context, conversationId, myUid, latest)
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

        val presenceFlow = PartnerRepositoryImpl.observeUserPresence(viewModelScope, context, partnerUid, 5000L)
        viewModelScope.launch {
            presenceFlow.collectLatest { presence ->
                _uiState.value = _uiState.value.copy(partnerPresence = presence)
            }
        }

        val typingFlow = ChatRepositoryImpl.observePartnerTyping(viewModelScope, context, conversationId, partnerUid, 2000L)
        viewModelScope.launch {
            typingFlow.collectLatest { isTyping ->
                _uiState.value = _uiState.value.copy(partnerIsTyping = isTyping)
            }
        }

        val readFlow = ChatRepositoryImpl.observePartnerReadState(viewModelScope, context, conversationId, partnerUid, 3000L)
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
            ChatRepositoryImpl.stopObservingMessages(convId)
            ChatRepositoryImpl.stopObservingTyping(convId)
            ChatRepositoryImpl.stopObservingPartnerReadState(convId)
        }
        val partnerUid = _uiState.value.partnerUid
        if (partnerUid.isNotBlank()) {
            PartnerRepositoryImpl.stopObservingPresence(partnerUid)
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
        val myUid = getMyUidOrEmpty(context)

        clearTypingState()

        val tempId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = text.trim(),
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
                    _uiState.value = _uiState.value.copy(isSending = false, error = e.message ?: "ارسال ناموفق بود")
                    _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                }
        }
    }

    fun sendImage(uri: Uri, caption: String = "") {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)

        clearTypingState()

        val tempId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
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
                _uiState.value = _uiState.value.copy(isUploadingImage = false, error = "خواندن تصویر ناموفق بود")
                _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                return@launch
            }

            MediaRepositoryImpl.uploadImage(context, bytes)
                .onSuccess { url ->
                    ChatRepositoryImpl.sendImageMessage(context, convId, myUid, url, caption, messageId = tempId)
                        .onSuccess {
                            _uiState.value = _uiState.value.copy(isUploadingImage = false)
                            _pendingMessages.value = _pendingMessages.value.map {
                                if (it.messageId == tempId) it.copy(status = MessageStatus.SENT, mediaUrl = url) else it
                            }
                        }
                        .onFailure { e ->
                            _uiState.value = _uiState.value.copy(isUploadingImage = false, error = e.message ?: "ارسال تصویر ناموفق بود")
                            _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                        }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isUploadingImage = false, error = e.message ?: "آپلود تصویر ناموفق بود")
                    _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
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

        val recorded = audioRecorder.stop()
        _uiState.value = _uiState.value.copy(isRecording = false, recordingDurationMs = 0L)

        if (recorded == null) {
            _uiState.value = _uiState.value.copy(recordingError = "ضبط صدا خیلی کوتاه بود یا ناموفق بود")
            return
        }

        val convId = conversationId ?: run {
            recorded.file.delete()
            return
        }
        val myUid = getMyUidOrEmpty(context)

        val tempId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val fileSize = recorded.file.length()

        val pendingMsg = Message(
            messageId = tempId,
            conversationId = convId,
            senderId = myUid,
            text = "",
            type = MessageType.AUDIO,
            mediaUrl = recorded.file.absolutePath, // پیش‌نمایش محلی موقت تا قبل از تأیید سرور
            createdAt = now,
            status = MessageStatus.PENDING,
            durationMs = recorded.durationMs,
            mimeType = recorded.mimeType,
            fileSize = fileSize
        )
        _pendingMessages.value = _pendingMessages.value + pendingMsg

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAudio = true, error = null)

            val bytes = withContext(Dispatchers.IO) {
                runCatching { recorded.file.readBytes() }.getOrNull()
            }

            if (bytes == null) {
                _uiState.value = _uiState.value.copy(isUploadingAudio = false, error = "خواندن فایل صوتی ناموفق بود")
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
                        messageId = tempId
                    ).onSuccess {
                        _uiState.value = _uiState.value.copy(isUploadingAudio = false)
                        _pendingMessages.value = _pendingMessages.value.map {
                            if (it.messageId == tempId) it.copy(status = MessageStatus.SENT, mediaUrl = url) else it
                        }
                        recorded.file.delete() // فایل موقت بعد از آپلود موفق حذف شود
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(isUploadingAudio = false, error = e.message ?: "ارسال پیام صوتی ناموفق بود")
                        _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
                        recorded.file.delete()
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isUploadingAudio = false, error = e.message ?: "آپلود صدا ناموفق بود")
                    _pendingMessages.value = _pendingMessages.value.filterNot { it.messageId == tempId }
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
        _uiState.value = _uiState.value.copy(editingMessageId = message.messageId)
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
                    _uiState.value = _uiState.value.copy(isSending = false, error = e.message ?: "ویرایش ناموفق بود")
                }
        }
    }

    fun deleteMessage(message: Message) {
        if (message.status == MessageStatus.PENDING) return
        val convId = conversationId ?: return
        viewModelScope.launch {
            ChatRepositoryImpl.deleteMessage(context, convId, message.messageId)
                .onSuccess { ChatRepositoryImpl.refreshNow(context, convId) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "حذف پیام ناموفق بود") }
        }
    }

    fun notifyTyping() {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)

        if (!typingActive) {
            typingActive = true
            viewModelScope.launch { ChatRepositoryImpl.setTypingState(context, convId, myUid, true) }
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
            viewModelScope.launch { ChatRepositoryImpl.setTypingState(context, convId, myUid, false) }
        }
    }

    fun setChatBackground(uri: String?) {
        val convId = conversationId ?: return
        ChatAppearancePrefs.setBackgroundUri(context, convId, uri)
        _uiState.value = _uiState.value.copy(backgroundUri = uri)
    }

    fun setChatTheme(themeKey: String) {
        val convId = conversationId ?: return
        ChatAppearancePrefs.setThemeKey(context, convId, themeKey)
        _uiState.value = _uiState.value.copy(themeKey = themeKey)
    }

    private fun compressImage(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("فایل قابل خواندن نیست")

        val original = input.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("فرمت تصویر پشتیبانی نمی‌شود")

        val scaled = scaleDown(original, maxImageDimensionPx)

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
        return output.toByteArray()
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
}