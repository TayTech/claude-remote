package com.clauderemote.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clauderemote.data.repository.ClaudeRemoteRepository
import com.clauderemote.data.repository.ServerSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TestConnectionState {
    data object Idle : TestConnectionState()
    data object Testing : TestConnectionState()
    data object Success : TestConnectionState()
    data class Error(val message: String) : TestConnectionState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ClaudeRemoteRepository
) : ViewModel() {

    companion object {
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535
        const val ERROR_EMPTY_HOST = "Enter server address"
        const val ERROR_EMPTY_API_KEY = "Enter API key"
        const val ERROR_INVALID_PORT = "Invalid port (must be 1-65535)"
        const val ERROR_CLAUDE_NOT_INSTALLED = "Claude CLI not installed on server"
        const val ERROR_CONNECTION_FAILED = "Connection failed"
        const val ERROR_SAVE_FAILED = "Save failed"
    }

    private val _settings = MutableStateFlow<ServerSettings?>(null)
    val settings: StateFlow<ServerSettings?> = _settings.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _port = MutableStateFlow("3190")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _testState = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testState: StateFlow<TestConnectionState> = _testState.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.serverSettings.collect { serverSettings ->
                _settings.value = serverSettings
                if (serverSettings != null) {
                    _host.value = serverSettings.host
                    _port.value = serverSettings.port.toString()
                    _apiKey.value = serverSettings.apiKey
                }
                _isLoading.value = false
            }
        }
    }

    fun updateHost(value: String) {
        _host.value = value
        _testState.value = TestConnectionState.Idle
        _saveError.value = null
    }

    fun updatePort(value: String) {
        _port.value = value.filter { it.isDigit() }
        _testState.value = TestConnectionState.Idle
        _saveError.value = null
    }

    fun updateApiKey(value: String) {
        _apiKey.value = value
        _testState.value = TestConnectionState.Idle
        _saveError.value = null
    }

    fun testConnection() {
        val hostValue = _host.value.trim()
        val portValue = _port.value.toIntOrNull() ?: 3190
        val apiKeyValue = _apiKey.value.trim()

        if (hostValue.isEmpty()) {
            _testState.value = TestConnectionState.Error(ERROR_EMPTY_HOST)
            return
        }

        if (apiKeyValue.isEmpty()) {
            _testState.value = TestConnectionState.Error(ERROR_EMPTY_API_KEY)
            return
        }

        // F13 fix: validate port range
        if (portValue < MIN_PORT || portValue > MAX_PORT) {
            _testState.value = TestConnectionState.Error(ERROR_INVALID_PORT)
            return
        }

        viewModelScope.launch {
            _testState.value = TestConnectionState.Testing

            val result = repository.testConnection(hostValue, portValue, apiKeyValue)
            _testState.value = result.fold(
                onSuccess = { health ->
                    if (health.claudeCliInstalled) {
                        TestConnectionState.Success
                    } else {
                        TestConnectionState.Error(ERROR_CLAUDE_NOT_INSTALLED)
                    }
                },
                onFailure = { error ->
                    TestConnectionState.Error(error.message ?: ERROR_CONNECTION_FAILED)
                }
            )
        }
    }

    fun saveSettings(onSuccess: () -> Unit) {
        val hostValue = _host.value.trim()
        val portValue = _port.value.toIntOrNull() ?: 3190
        val apiKeyValue = _apiKey.value.trim()

        if (hostValue.isEmpty()) {
            _saveError.value = ERROR_EMPTY_HOST
            return
        }

        if (apiKeyValue.isEmpty()) {
            _saveError.value = ERROR_EMPTY_API_KEY
            return
        }

        // F13 fix: validate port range
        if (portValue < MIN_PORT || portValue > MAX_PORT) {
            _saveError.value = ERROR_INVALID_PORT
            return
        }

        viewModelScope.launch {
            try {
                repository.saveServerSettings(hostValue, portValue, apiKeyValue)
                repository.connect()
                onSuccess()
            } catch (e: Exception) {
                _saveError.value = e.message ?: ERROR_SAVE_FAILED
            }
        }
    }
}
