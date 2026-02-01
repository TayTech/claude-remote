package com.clauderemote.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val path: String,
    val devServerPort: Int? = null
)

@Serializable
data class Session(
    val sessionId: String,
    val summary: String = "",
    val firstPrompt: String = "",
    val messageCount: Int = 0,
    val created: String = "",
    val modified: String = "",
    val gitBranch: String = ""
)

@Serializable
data class HealthResponse(
    val status: String,
    val uptime: Double,
    val claudeCliInstalled: Boolean
)

@Serializable
data class OutputChunk(
    val executionId: String,
    val correlationId: String,
    val type: String, // stdout, stderr, system
    val content: String,
    val timestamp: Long
)

@Serializable
data class CommandComplete(
    val executionId: String,
    val correlationId: String,
    val exitCode: Int,
    val sessionId: String,
    val duration: Long
)

@Serializable
data class CommandError(
    val executionId: String,
    val correlationId: String,
    val error: String,
    val code: String // PROCESS_FAILED, TIMEOUT, CANCELLED, SESSION_NOT_FOUND
)

@Serializable
data class SendCommandPayload(
    val projectId: String,
    val sessionId: String?,
    val command: String,
    val correlationId: String
)

@Serializable
data class SendCommandAck(
    val success: Boolean,
    val executionId: String,
    val correlationId: String,
    val error: String? = null
)

@Serializable
data class CancelCommandPayload(
    val executionId: String
)

@Serializable
data class CancelCommandAck(
    val success: Boolean,
    val error: String? = null
)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

@Serializable
data class HistoryMessage(
    val role: String,
    val content: String,
    val timestamp: String = ""
)

// PTY-related models
@Serializable
data class PtyInputPayload(
    val executionId: String,
    val data: String
)

@Serializable
data class PtyResizePayload(
    val executionId: String,
    val cols: Int,
    val rows: Int
)

@Serializable
data class PtyAck(
    val success: Boolean,
    val error: String? = null
)

@Serializable
data class StartPtyAck(
    val success: Boolean,
    val executionId: String,
    val error: String? = null
)
