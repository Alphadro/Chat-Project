package fit.vcare.apps.viewmodel


import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.domain.model.PartnerError
import fit.vcare.apps.domain.model.PartnerInvite
import fit.vcare.apps.domain.model.PartnerUserInfo
import fit.vcare.apps.data.repository.ChatRepositoryImpl
import fit.vcare.apps.data.repository.PartnerException
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// TODO(integration): اگر دامنه واقعی خودتان را دارید این دو مقدار را عوض کنید
private const val DEEP_LINK_HTTPS = "https://YOUR_DOMAIN/partner/invite/"
private const val DEEP_LINK_SCHEME = "vcareapp://partner/invite/"

data class PartnerUiState(
    val isLoading: Boolean = false,
    val invite: PartnerInvite? = null,
    val inviteLink: String? = null,
    val previewUser: PartnerUserInfo? = null,
    val previewToken: String? = null,
    val error: PartnerError? = null,
    val acceptedRelationshipId: String? = null,
    val acceptedConversationId: String? = null,
    val acceptedPartnerUid: String? = null,
    val acceptedPartnerName: String? = null
)

class PartnerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(PartnerUiState())
    val uiState: StateFlow<PartnerUiState> = _uiState

    fun generateInvite() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            PartnerRepositoryImpl.createInvite(context)
                .onSuccess { invite ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        invite = invite,
                        inviteLink = "$DEEP_LINK_HTTPS${invite.token}"
                    )
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = mapError(e)) }
        }
    }

    /** استخراج token از لینک اسکن‌شده (https یا scheme سفارشی) */
    fun extractTokenFromScannedText(scanned: String): String? {
        return try {
            val uri = Uri.parse(scanned)
            when {
                scanned.startsWith(DEEP_LINK_HTTPS) -> scanned.removePrefix(DEEP_LINK_HTTPS)
                scanned.startsWith(DEEP_LINK_SCHEME) -> scanned.removePrefix(DEEP_LINK_SCHEME)
                uri.lastPathSegment != null -> uri.lastPathSegment
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun loadInvitePreview(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, previewToken = token)
            val inviteResult = PartnerRepositoryImpl.getInvite(context, token)
            val invite = inviteResult.getOrElse {
                _uiState.value = _uiState.value.copy(isLoading = false, error = mapError(it))
                return@launch
            }
            val userResult = PartnerRepositoryImpl.getUserBasicInfo(context, invite.createdBy)
            userResult
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(isLoading = false, invite = invite, previewUser = user)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = mapError(e)) }
        }
    }

    fun acceptInvite(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val relationshipResult = PartnerRepositoryImpl.acceptInvite(context, token)
            val relationship = relationshipResult.getOrElse {
                _uiState.value = _uiState.value.copy(isLoading = false, error = mapError(it))
                return@launch
            }

            val myUid = fit.vcare.apps.data.repository.getMyUidOrEmpty(context)
            val partnerUid = relationship.otherUid(myUid)

            val convResult = ChatRepositoryImpl.getOrCreateConversation(
                context, relationship.relationshipId, listOf(relationship.userA, relationship.userB)
            )
            val conversation = convResult.getOrElse {
                _uiState.value = _uiState.value.copy(isLoading = false, error = PartnerError.NetworkError)
                return@launch
            }

            val partnerInfo = PartnerRepositoryImpl.getUserBasicInfo(context, partnerUid).getOrNull()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                acceptedRelationshipId = relationship.relationshipId,
                acceptedConversationId = conversation.conversationId,
                acceptedPartnerUid = partnerUid,
                acceptedPartnerName = partnerInfo?.displayName ?: partnerUid
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun mapError(e: Throwable): PartnerError =
        if (e is PartnerException) e.error else PartnerError.Unknown(e.message ?: "خطای نامشخص")
}