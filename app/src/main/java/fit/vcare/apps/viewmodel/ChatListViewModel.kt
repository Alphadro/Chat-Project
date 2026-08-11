package fit.vcare.apps.viewmodel


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatListItemUiState(
    val relationshipId: String,
    val conversationId: String,
    val partnerUid: String,
    val partnerName: String,
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

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val relationshipsResult = PartnerRepositoryImpl.getMyActiveRelationships(context)
            val relationships = relationshipsResult.getOrElse {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                return@launch
            }

            if (relationships.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, items = emptyList())
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
                    lastMessage = conversation.lastMessage,
                    lastMessageAt = conversation.lastMessageAt
                )
            }.sortedByDescending { it.lastMessageAt ?: 0L }

            _uiState.value = _uiState.value.copy(isLoading = false, items = items)
        }
    }
}