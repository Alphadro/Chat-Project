package fit.vcare.apps.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fit.vcare.apps.data.repository.PartnerRepositoryImpl
import fit.vcare.apps.domain.model.PartnerPresence
import fit.vcare.apps.domain.model.PartnerUserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PartnerProfileUiState(
    val isLoading: Boolean = true,
    val userInfo: PartnerUserInfo? = null,
    val presence: PartnerPresence? = null,
    val error: String? = null
)

class PartnerProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val _uiState = MutableStateFlow(PartnerProfileUiState())
    val uiState: StateFlow<PartnerProfileUiState> = _uiState

    private var observingUid: String? = null

    fun load(partnerUid: String) {
        if (observingUid == partnerUid) return
        observingUid = partnerUid

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            PartnerRepositoryImpl.getUserBasicInfo(context, partnerUid)
                .onSuccess { info ->
                    _uiState.value = _uiState.value.copy(isLoading = false, userInfo = info)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "خطا در دریافت اطلاعات")
                }
        }

        val presenceFlow = PartnerRepositoryImpl.observeUserPresence(viewModelScope, context, partnerUid, 5000L)
        viewModelScope.launch {
            presenceFlow.collectLatest { presence ->
                _uiState.value = _uiState.value.copy(presence = presence)
            }
        }
    }

    fun stopObserving() {
        observingUid?.let { PartnerRepositoryImpl.stopObservingPresence(it) }
        observingUid = null
    }
}