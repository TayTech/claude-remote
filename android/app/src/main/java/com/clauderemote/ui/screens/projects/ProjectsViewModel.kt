package com.clauderemote.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clauderemote.data.model.ConnectionState
import com.clauderemote.data.model.Project
import com.clauderemote.data.repository.ClaudeRemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProjectsUiState {
    data object Loading : ProjectsUiState()
    data class Success(val projects: List<Project>) : ProjectsUiState()
    data class Error(val message: String) : ProjectsUiState()
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ClaudeRemoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    init {
        loadProjects()
        connectSocket()
    }

    private fun connectSocket() {
        viewModelScope.launch {
            repository.connect()
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = ProjectsUiState.Loading

            val result = repository.getProjects()
            _uiState.value = result.fold(
                onSuccess = { projects -> ProjectsUiState.Success(projects) },
                onFailure = { error -> ProjectsUiState.Error(error.message ?: "Unknown error") }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.getProjects()
            _uiState.value = result.fold(
                onSuccess = { projects -> ProjectsUiState.Success(projects) },
                onFailure = { error -> ProjectsUiState.Error(error.message ?: "Unknown error") }
            )
            _isRefreshing.value = false
        }
    }

    fun saveLastProject(projectId: String) {
        viewModelScope.launch {
            repository.saveLastProjectId(projectId)
        }
    }
}
