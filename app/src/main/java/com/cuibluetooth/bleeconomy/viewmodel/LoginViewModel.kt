package com.cuibluetooth.bleeconomy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuibluetooth.bleeconomy.repository.PermitRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class LoginEvent {
    data class Success(
        val permitCode: String,
        val assignedUuid: String,
        val expiryEpochMillis: Long?
    ) : LoginEvent()
}
class LoginViewModel : ViewModel() {
    private val permitRepository = PermitRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>(replay = 0)
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun verifyPermit(permitCode: String, assignedUuid: String) {
        val sanitizedPermit = permitCode.trim()
        val sanitizedUuid = assignedUuid.trim()

        if (sanitizedPermit.isEmpty() || sanitizedUuid.isEmpty()) {
            _uiState.value = LoginUiState(
                isLoading = false,
                errorMessage = "Permit code and device identifier are required."
            )
            return
        }

        _uiState.value = LoginUiState(isLoading = true)

        viewModelScope.launch {
            try {
                val result = permitRepository.verify(sanitizedPermit, sanitizedUuid)
                _uiState.value = LoginUiState(isLoading = false)
                _events.emit(
                    LoginEvent.Success(
                        permitCode = result.permitCode,
                        assignedUuid = result.assignedUuid,
                        expiryEpochMillis = result.expiryEpochMillis
                    )
                )
            } catch (throwable: Throwable) {
                val message = throwable.message ?: "Failed to verify permit."
                _uiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}