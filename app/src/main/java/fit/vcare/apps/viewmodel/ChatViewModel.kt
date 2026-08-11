package fit.vcare.apps.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.data.remote.MediaRepositoryImpl
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import fit.vcare.apps.data.repository.getMyUidOrEmpty
import fit.vcare.apps.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class ChatUiState(
    val isLoading: Boolean = false,
    val messages: List<Message> = emptyList(),
    val partnerName: String = "",
    val currentUid: String = "",
    val partnerUid: String = "",
    val isSending: Boolean = false,
    val isUploadingImage: Boolean = false,
    val editingMessageId: String? = null,
    val partnerLastSeen: Long? = null,
    val partnerIsTyping: Boolean = false,
    val partnerLastReadAt: Long? = null,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private var conversationId: String? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val maxImageDimensionPx = 1280
    private val jpegQuality = 70

    private var lastWrittenReadAt: Long = 0L
    private var heartbeatJob: Job? = null

    private var typingActive = false
    private var stopTypingJob: Job? = null

    fun startObserving(conversationId: String, partnerUid: String, partnerName: String) {
        this.conversationId = conversationId
        val myUid = getMyUidOrEmpty(context)
        lastWrittenReadAt = 0L

        _uiState.value = _uiState.value.copy(
            partnerName = partnerName,
            partnerUid = partnerUid,
            currentUid = myUid,
            isLoading = true
        )

        // پیام‌ها
        val messagesFlow = ChatRepositoryImpl.observeMessages(viewModelScope, context, conversationId, 3000L)
        viewModelScope.launch {
            messagesFlow.collectLatest { messages ->
                _uiState.value = _uiState.value.copy(isLoading = false, messages = messages)
                val latest = messages.lastOrNull()?.createdAt ?: 0L
                if (latest > lastWrittenReadAt) {
                    lastWrittenReadAt = latest
                    ChatRepositoryImpl.updateLastRead(context, conversationId, myUid, latest)
                }
            }
        }

        // آخرین بازدید/آنلاین بودن پارتنر
        val presenceFlow = PartnerRepositoryImpl.observeUserPresence(viewModelScope, context, partnerUid, 15000L)
        viewModelScope.launch {
            presenceFlow.collectLatest { lastSeen ->
                _uiState.value = _uiState.value.copy(partnerLastSeen = lastSeen)
            }
        }

        // وضعیت تایپ پارتنر
        val typingFlow = ChatRepositoryImpl.observePartnerTyping(viewModelScope, context, conversationId, partnerUid, 2000L)
        viewModelScope.launch {
            typingFlow.collectLatest { isTyping ->
                _uiState.value = _uiState.value.copy(partnerIsTyping = isTyping)
            }
        }

        // آخرین زمان خواندن پارتنر (برای تیک دوبل)
        val readFlow = ChatRepositoryImpl.observePartnerReadState(viewModelScope, context, conversationId, partnerUid, 3000L)
        viewModelScope.launch {
            readFlow.collectLatest { lastReadAt ->
                _uiState.value = _uiState.value.copy(partnerLastReadAt = lastReadAt)
            }
        }

        // Heartbeat خودم (فقط تا وقتی این صفحه باز است)
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                PartnerRepositoryImpl.sendHeartbeat(context)
                delay(25000L)
            }
        }
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
        heartbeatJob?.cancel()
        clearTypingState()
    }

    fun sendMessage(text: String) {
        val convId = conversationId ?: return
        if (text.isBlank()) return
        val myUid = getMyUidOrEmpty(context)

        clearTypingState()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            ChatRepositoryImpl.sendMessage(context, convId, myUid, text)
                .onSuccess { _uiState.value = _uiState.value.copy(isSending = false) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isSending = false, error = e.message ?: "ارسال ناموفق بود") }
        }
    }

    fun sendImage(uri: Uri, caption: String = "") {
        val convId = conversationId ?: return
        val myUid = getMyUidOrEmpty(context)

        clearTypingState()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingImage = true, error = null)

            val bytesResult = withContext(Dispatchers.IO) {
                runCatching { compressImage(uri) }
            }

            val bytes = bytesResult.getOrNull()
            if (bytes == null) {
                _uiState.value = _uiState.value.copy(isUploadingImage = false, error = "خواندن تصویر ناموفق بود")
                return@launch
            }

            MediaRepositoryImpl.uploadImage(context, bytes)
                .onSuccess { url ->
                    ChatRepositoryImpl.sendImageMessage(context, convId, myUid, url, caption)
                        .onSuccess { _uiState.value = _uiState.value.copy(isUploadingImage = false) }
                        .onFailure { e ->
                            _uiState.value = _uiState.value.copy(isUploadingImage = false, error = e.message ?: "ارسال تصویر ناموفق بود")
                        }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isUploadingImage = false, error = e.message ?: "آپلود تصویر ناموفق بود")
                }
        }
    }

    fun startEditingMessage(message: Message) {
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
        val convId = conversationId ?: return
        viewModelScope.launch {
            ChatRepositoryImpl.deleteMessage(context, convId, message.messageId)
                .onSuccess { ChatRepositoryImpl.refreshNow(context, convId) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "حذف پیام ناموفق بود") }
        }
    }

    /** هر بار متن کادر تایپ تغییر کرد، این را صدا بزنید. خودش debounce می‌کند و هر keystroke را write نمی‌کند. */
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
        stopObserving()
    }
}