package com.clauderemote.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clauderemote.data.model.Session
import com.clauderemote.data.repository.ClaudeRemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SessionsUiState {
    data object Loading : SessionsUiState()
    data class Success(val sessions: List<Session>) : SessionsUiState()
    data class Error(val message: String) : SessionsUiState()
}

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: ClaudeRemoteRepository
) : ViewModel() {

    companion object {
        private const val ERROR_UNKNOWN = "Unknown error"
    }

    private val _uiState = MutableStateFlow<SessionsUiState>(SessionsUiState.Loading)
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentProjectId: String? = null

    fun loadSessions(projectId: String) {
        currentProjectId = projectId
        viewModelScope.launch {
            _uiState.value = SessionsUiState.Loading

            val result = repository.getSessions(projectId)
            _uiState.value = result.fold(
                onSuccess = { sessions -> SessionsUiState.Success(sessions) },
                onFailure = { error -> SessionsUiState.Error(error.message ?: ERROR_UNKNOWN) }
            )
        }
    }

    fun refresh() {
        val projectId = currentProjectId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true

            val result = repository.getSessions(projectId)
            _uiState.value = result.fold(
                onSuccess = { sessions -> SessionsUiState.Success(sessions) },
                onFailure = { error -> SessionsUiState.Error(error.message ?: ERROR_UNKNOWN) }
            )

            _isRefreshing.value = false
        }
    }
}
