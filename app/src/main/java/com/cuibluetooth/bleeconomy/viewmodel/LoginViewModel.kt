package com.cuibluetooth.bleeconomy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuibluetooth.bleeconomy.repository.AuthRepository
import com.cuibluetooth.bleeconomy.repository.PermitRepository
import com.cuibluetooth.bleeconomy.repository.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    data class Success(val session: SessionStore.Session) : LoginUiState()
}
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val permitRepository: PermitRepository,
    private val sessionStore: SessionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun submit(username: String, password: String, permitCode: String) {
        viewModelScope.launch(dispatcher) {
            _uiState.value = LoginUiState.Loading
            try {
                val authResult = authRepository.authenticate(username.trim(), password)
                val permitResult = permitRepository.verify(permitCode.trim(), authResult.userUuid)
                val session = SessionStore.Session(
                    username = authResult.username,
                    displayName = authResult.displayName,
                    userUuid = authResult.userUuid,
                    assignedUuid = permitResult.assignedUuid,
                    permitCode = permitResult.permitCode,
                    expiryEpochMillis = permitResult.expiryEpochMillis
                )
                sessionStore.saveSession(session)
                _uiState.value = LoginUiState.Success(session)
            } catch (t: Throwable) {
                val message = t.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to verify credentials"
                _uiState.value = LoginUiState.Error(message)
            }
        }
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val permitRepository: PermitRepository,
        private val sessionStore: SessionStore,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(authRepository, permitRepository, sessionStore, dispatcher) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${'$'}modelClass")
        }
    }
}