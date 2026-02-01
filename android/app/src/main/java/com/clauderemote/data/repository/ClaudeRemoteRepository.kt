package com.clauderemote.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clauderemote.data.model.CommandComplete
import com.clauderemote.data.model.CommandError
import com.clauderemote.data.model.ConnectionState
import com.clauderemote.data.model.HealthResponse
import com.clauderemote.data.model.HistoryMessage
import com.clauderemote.data.model.OutputChunk
import com.clauderemote.data.model.Project
import com.clauderemote.data.model.PtyAck
import com.clauderemote.data.model.SendCommandAck
import com.clauderemote.data.model.Session
import com.clauderemote.data.model.StartPtyAck
import com.clauderemote.data.remote.ApiService
import com.clauderemote.data.remote.SocketService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

data class ServerSettings(
    val host: String,
    val port: Int,
    val apiKey: String
)

class ClaudeRemoteRepository @Inject constructor(
    private val apiService: ApiService,
    private val socketService: SocketService,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_PORT = intPreferencesKey("server_port")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_LAST_PROJECT_ID = stringPreferencesKey("last_project_id")

        const val DEFAULT_PORT = 3190
        const val ERROR_SERVER_NOT_CONFIGURED = "Server not configured"
    }

    // DataStore flows
    val serverSettings: Flow<ServerSettings?> = dataStore.data.map { prefs ->
        val host = prefs[KEY_HOST]
        val port = prefs[KEY_PORT] ?: DEFAULT_PORT
        val apiKey = prefs[KEY_API_KEY] ?: ""
        if (host != null && apiKey.isNotEmpty()) ServerSettings(host, port, apiKey) else null
    }

    val lastProjectId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_PROJECT_ID]
    }

    // Connection state
    val connectionState: StateFlow<ConnectionState> = socketService.connectionState

    // Socket events
    val outputChunks: SharedFlow<OutputChunk> = socketService.outputChunks
    val commandComplete: SharedFlow<CommandComplete> = socketService.commandComplete
    val commandError: SharedFlow<CommandError> = socketService.commandError

    // Settings management
    suspend fun saveServerSettings(host: String, port: Int, apiKey: String) {
        dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_PORT] = port
            prefs[KEY_API_KEY] = apiKey
        }
    }

    suspend fun saveLastProjectId(projectId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_PROJECT_ID] = projectId
        }
    }

    suspend fun getServerSettings(): ServerSettings? {
        return serverSettings.first()
    }

    // API calls
    suspend fun testConnection(host: String, port: Int, apiKey: String): Result<HealthResponse> {
        apiService.setBaseUrl(host, port, apiKey)
        return apiService.getHealth()
    }

    suspend fun getProjects(): Result<List<Project>> {
        val settings = getServerSettings()
            ?: return Result.failure(Exception(ERROR_SERVER_NOT_CONFIGURED))

        apiService.setBaseUrl(settings.host, settings.port, settings.apiKey)
        return apiService.getProjects()
    }

    suspend fun getSessions(projectId: String): Result<List<Session>> {
        val settings = getServerSettings()
            ?: return Result.failure(Exception(ERROR_SERVER_NOT_CONFIGURED))

        apiService.setBaseUrl(settings.host, settings.port, settings.apiKey)
        return apiService.getSessions(projectId)
    }

    suspend fun getSessionHistory(projectId: String, sessionId: String): Result<List<HistoryMessage>> {
        val settings = getServerSettings()
            ?: return Result.failure(Exception(ERROR_SERVER_NOT_CONFIGURED))

        apiService.setBaseUrl(settings.host, settings.port, settings.apiKey)
        return apiService.getSessionHistory(projectId, sessionId)
    }

    // Socket management
    suspend fun connect() {
        val settings = getServerSettings() ?: return
        socketService.connect(settings.host, settings.port, settings.apiKey)
    }

    fun disconnect() {
        socketService.disconnect()
    }

    fun isConnected(): Boolean = socketService.isConnected()

    // Command execution
    fun sendCommand(
        projectId: String,
        sessionId: String?,
        command: String,
        onAck: (SendCommandAck) -> Unit
    ): String {
        val correlationId = UUID.randomUUID().toString()
        socketService.sendCommand(projectId, sessionId, command, correlationId, onAck)
        return correlationId
    }

    fun cancelCommand(executionId: String, onAck: (Boolean, String?) -> Unit) {
        socketService.cancelCommand(executionId, onAck)
    }

    // PTY methods
    fun sendPtyInput(executionId: String, data: String, onAck: (PtyAck) -> Unit = {}) {
        socketService.sendPtyInput(executionId, data, onAck)
    }

    fun sendPtyResize(executionId: String, cols: Int, rows: Int, onAck: (PtyAck) -> Unit = {}) {
        socketService.sendPtyResize(executionId, cols, rows, onAck)
    }

    fun startPty(
        projectId: String,
        sessionId: String?,
        cols: Int,
        rows: Int,
        onAck: (StartPtyAck) -> Unit
    ) {
        socketService.startPty(projectId, sessionId, cols, rows, onAck)
    }
}
