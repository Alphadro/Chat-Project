package fit.vcare.apps.viewmodel


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.data.mapper.toChatListItems
import fit.vcare.apps.data.mapper.toJsonArray
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import fit.vcare.apps.data.repository.LocalDataCache
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import fit.vcare.apps.data.repository.getMyUidOrEmpty
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
//ChatListViewModel.kt
data class ChatListItemUiState(
    val relationshipId: String,
    val conversationId: String,
    val partnerUid: String,
    val partnerName: String,
    val partnerPhotoUrl: String? = null,
    val lastMessage: String?,
    val lastMessageAt: Long?,
    val unreadCount: Long = 0
)

data class ChatListUiState(
    val isLoading: Boolean = false,
    val items: List<ChatListItemUiState> = emptyList(),
    val error: String? = null
)

class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    private var pollingJob: Job? = null
    private val CHAT_LIST_POLL_INTERVAL_MS = 4000L   // قابل تنظیم

    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState

    companion object {
        private const val CACHE_KEY_CHAT_LIST = "chat_list_items"
    }
    private suspend fun refresh(isInitial: Boolean) {
        if (isInitial) {
            // ۱) اول کش محلی رو فوری نشون بده (بدون هیچ لودینگی)
            val cachedJson = LocalDataCache.getString(context, CACHE_KEY_CHAT_LIST)
            val cachedItems = cachedJson?.let {
                runCatching { JSONArray(it).toChatListItems() }.getOrNull()
            }

            if (cachedItems != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, items = cachedItems, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

            fetchAndApply(hadDataAlready = cachedItems != null)
        } else {
            // نسخه‌ی بی‌سروصدا: isLoading اصلاً دست‌کاری نمی‌شه، کاربر چیزی نمی‌بینه چشمک بزنه
            fetchAndApply(hadDataAlready = true)
        }
    }
    /** ۲) گرفتن اطلاعات تازه از سرور و اعمال روی state — مشترک بین initial و polling سکوت */
    private suspend fun fetchAndApply(hadDataAlready: Boolean) {
        val relationshipsResult = PartnerRepositoryImpl.getMyActiveRelationships(context)
        val relationships = relationshipsResult.getOrElse {
            // آفلاین یا خطا -> اگه قبلاً چیزی روی صفحه بود همونو نگه‌دار، وگرنه خطا نشون بده
            if (!hadDataAlready) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            return
        }

        if (relationships.isEmpty()) {
            _uiState.value = _uiState.value.copy(isLoading = false, items = emptyList(), error = null)
            LocalDataCache.putString(context, CACHE_KEY_CHAT_LIST, JSONArray().toString())
            return
        }

        val myUid = getMyUidOrEmpty(context)

        val items = coroutineScope {
            relationships.map { entry ->
                async {
                    val conversation = ChatRepositoryImpl.getConversation(context, entry.relationshipId).getOrNull()
                    val partnerInfo = PartnerRepositoryImpl.getUserBasicInfo(context, entry.partnerUid).getOrNull()
                    if (conversation == null) return@async null
                    ChatListItemUiState(
                        relationshipId = entry.relationshipId,
                        conversationId = conversation.conversationId,
                        partnerUid = entry.partnerUid,
                        partnerName = partnerInfo?.displayName ?: entry.partnerUid,
                        partnerPhotoUrl = partnerInfo?.photoUrl,
                        lastMessage = conversation.lastMessage,
                        lastMessageAt = conversation.lastMessageAt
                    )
                }
            }.awaitAll().filterNotNull()
        }.sortedByDescending { it.lastMessageAt ?: 0L }

        _uiState.value = _uiState.value.copy(isLoading = false, items = items, error = null)
        LocalDataCache.putString(context, CACHE_KEY_CHAT_LIST, items.toJsonArray().toString())
    }

    fun startObserving() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            refresh(isInitial = true)
            while (isActive) {
                delay(CHAT_LIST_POLL_INTERVAL_MS)
                refresh(isInitial = false)
            }
        }
    }

    fun stopObserving() {
        pollingJob?.cancel()
        pollingJob = null
    }

}