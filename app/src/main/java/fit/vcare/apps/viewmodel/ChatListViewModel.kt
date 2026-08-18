package fit.vcare.apps.viewmodel


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.data.mapper.toChatListItems
import fit.vcare.apps.data.mapper.toJsonArray
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import fit.vcare.apps.data.repository.LocalDataCache
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

data class ChatListItemUiState(
    val relationshipId: String,
    val conversationId: String,
    val partnerUid: String,
    val partnerName: String,
    val partnerPhotoUrl: String? = null,
    val lastMessage: String?,
    val lastMessageAt: Long?
)

data class ChatListUiState(
    val isLoading: Boolean = false,
    val items: List<ChatListItemUiState> = emptyList(),
    val error: String? = null
)

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState

    companion object {
        private const val CACHE_KEY_CHAT_LIST = "chat_list_items"
    }

    fun load() {
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

        // ۲) در پس‌زمینه تلاش کن اطلاعات تازه بگیری (اگه آنلاین باشه موفق می‌شه، وگرنه بی‌سروصدا fail می‌شه و کش همچنان روی صفحه می‌مونه)
        viewModelScope.launch {
            val relationshipsResult = PartnerRepositoryImpl.getMyActiveRelationships(context)
            val relationships = relationshipsResult.getOrElse {
                // آفلاین یا خطا -> اگه کش داشتیم همونو نگه‌دار، وگرنه خطا نشون بده
                if (cachedItems == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                return@launch
            }

            if (relationships.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, items = emptyList())
                LocalDataCache.putString(context, CACHE_KEY_CHAT_LIST, JSONArray().toString())
                return@launch
            }

            val items = relationships.mapNotNull { entry ->
                val conversation = ChatRepositoryImpl.getConversation(context, entry.relationshipId).getOrNull()
                val partnerInfo = PartnerRepositoryImpl.getUserBasicInfo(context, entry.partnerUid).getOrNull()
                if (conversation == null) return@mapNotNull null
                ChatListItemUiState(
                    relationshipId = entry.relationshipId,
                    conversationId = conversation.conversationId,
                    partnerUid = entry.partnerUid,
                    partnerName = partnerInfo?.displayName ?: entry.partnerUid,
                    partnerPhotoUrl = partnerInfo?.photoUrl,
                    lastMessage = conversation.lastMessage,
                    lastMessageAt = conversation.lastMessageAt
                )
            }.sortedByDescending { it.lastMessageAt ?: 0L }

            _uiState.value = _uiState.value.copy(isLoading = false, items = items, error = null)
            LocalDataCache.putString(context, CACHE_KEY_CHAT_LIST, items.toJsonArray().toString())
        }
    }
}