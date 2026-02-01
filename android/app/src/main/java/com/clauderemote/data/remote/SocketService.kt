package com.clauderemote.data.remote

import android.util.Log
import com.clauderemote.data.model.CommandComplete
import com.clauderemote.data.model.CommandError
import com.clauderemote.data.model.ConnectionState
import com.clauderemote.data.model.OutputChunk
import com.clauderemote.data.model.PtyAck
import com.clauderemote.data.model.SendCommandAck
import com.clauderemote.data.model.StartPtyAck
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

class SocketService @Inject constructor() {
    companion object {
        private const val TAG = "SocketService"
        private const val INITIAL_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val MAX_RECONNECT_ATTEMPTS = 10

        // Error messages (F8 fix - centralized for future i18n)
        const val ERROR_NOT_CONNECTED = "Not connected to server"
        const val ERROR_RECONNECT_FAILED = "Unable to reconnect"
        const val ERROR_INVALID_RESPONSE = "Invalid response"
        const val ERROR_UNKNOWN = "Unknown error"
    }

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    // Thread-safe access with @Volatile (F10 fix)
    @Volatile private var socket: Socket? = null
    @Volatile private var currentHost: String? = null
    @Volatile private var currentPort: Int? = null
    @Volatile private var reconnectAttempt = 0

    private val lock = Any()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _outputChunks = MutableSharedFlow<OutputChunk>(extraBufferCapacity = 100)
    val outputChunks: SharedFlow<OutputChunk> = _outputChunks.asSharedFlow()

    private val _commandComplete = MutableSharedFlow<CommandComplete>(extraBufferCapacity = 10)
    val commandComplete: SharedFlow<CommandComplete> = _commandComplete.asSharedFlow()

    private val _commandError = MutableSharedFlow<CommandError>(extraBufferCapacity = 10)
    val commandError: SharedFlow<CommandError> = _commandError.asSharedFlow()

    private val pendingAcks = mutableMapOf<String, (SendCommandAck) -> Unit>()

    fun connect(host: String, port: Int) {
        synchronized(lock) {
            if (socket?.connected() == true && host == currentHost && port == currentPort) {
                return
            }

            disconnect()
            currentHost = host
            currentPort = port
            reconnectAttempt = 0
            // Recreate scope after disconnect cancelled it
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        doConnect()
    }

    private fun doConnect() {
        val host = currentHost ?: return
        val port = currentPort ?: return

        try {
            _connectionState.value = ConnectionState.Connecting
            Log.d(TAG, "Connecting to $host:$port")

            val options = IO.Options().apply {
                forceNew = true
                reconnection = false // We handle reconnection manually
                timeout = 10000
                transports = arrayOf("websocket")
            }

            socket = IO.socket("http://$host:$port", options).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Connected")
                    reconnectAttempt = 0
                    _connectionState.value = ConnectionState.Connected
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Disconnected")
                    _connectionState.value = ConnectionState.Disconnected
                    scheduleReconnect()
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val error = args.firstOrNull()?.toString() ?: "Unknown error"
                    Log.e(TAG, "Connection error: $error")
                    _connectionState.value = ConnectionState.Error(error)
                    scheduleReconnect()
                }

                on("output-chunk") { args ->
                    try {
                        val data = args.firstOrNull() as? JSONObject ?: return@on
                        val chunk = OutputChunk(
                            executionId = data.getString("executionId"),
                            correlationId = data.getString("correlationId"),
                            type = data.getString("type"),
                            content = data.getString("content"),
                            timestamp = data.getLong("timestamp")
                        )
                        scope.launch { _outputChunks.emit(chunk) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing output-chunk", e)
                    }
                }

                on("command-complete") { args ->
                    try {
                        val data = args.firstOrNull() as? JSONObject ?: return@on
                        val complete = CommandComplete(
                            executionId = data.getString("executionId"),
                            correlationId = data.getString("correlationId"),
                            exitCode = data.getInt("exitCode"),
                            sessionId = data.getString("sessionId"),
                            duration = data.getLong("duration")
                        )
                        scope.launch { _commandComplete.emit(complete) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing command-complete", e)
                    }
                }

                on("command-error") { args ->
                    try {
                        val data = args.firstOrNull() as? JSONObject ?: return@on
                        val error = CommandError(
                            executionId = data.getString("executionId"),
                            correlationId = data.getString("correlationId"),
                            error = data.getString("error"),
                            code = data.getString("code")
                        )
                        scope.launch { _commandError.emit(error) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing command-error", e)
                    }
                }

                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create socket", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Failed to connect")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached")
            _connectionState.value = ConnectionState.Error(ERROR_RECONNECT_FAILED)
            return
        }

        reconnectAttempt++
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt)

        val delayMs = min(
            INITIAL_DELAY_MS * BACKOFF_MULTIPLIER.pow(reconnectAttempt - 1).toLong(),
            MAX_DELAY_MS
        )

        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${delayMs}ms")

        scope.launch {
            delay(delayMs)
            if (_connectionState.value is ConnectionState.Reconnecting) {
                doConnect()
            }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            scope.cancel() // F7 fix: cancel coroutines to prevent memory leak
            socket?.disconnect()
            socket?.off()
            socket = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun sendCommand(
        projectId: String,
        sessionId: String?,
        command: String,
        correlationId: String,
        onAck: (SendCommandAck) -> Unit
    ) {
        val sock = socket
        if (sock == null || !sock.connected()) {
            onAck(SendCommandAck(
                success = false,
                executionId = "",
                correlationId = correlationId,
                error = ERROR_NOT_CONNECTED
            ))
            return
        }

        val payload = JSONObject().apply {
            put("projectId", projectId)
            put("sessionId", sessionId ?: JSONObject.NULL)
            put("command", command)
            put("correlationId", correlationId)
        }

        sock.emit("send-command", payload, Ack { args ->
            try {
                val response = args.firstOrNull() as? JSONObject
                if (response != null) {
                    val ack = SendCommandAck(
                        success = response.getBoolean("success"),
                        executionId = response.getString("executionId"),
                        correlationId = response.getString("correlationId"),
                        error = response.optString("error", null)
                    )
                    onAck(ack)
                } else {
                    onAck(SendCommandAck(
                        success = false,
                        executionId = "",
                        correlationId = correlationId,
                        error = ERROR_INVALID_RESPONSE
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing send-command ack", e)
                onAck(SendCommandAck(
                    success = false,
                    executionId = "",
                    correlationId = correlationId,
                    error = e.message ?: ERROR_UNKNOWN
                ))
            }
        })
    }

    fun cancelCommand(executionId: String, onAck: (Boolean, String?) -> Unit) {
        val sock = socket
        if (sock == null || !sock.connected()) {
            onAck(false, ERROR_NOT_CONNECTED)
            return
        }

        val payload = JSONObject().apply {
            put("executionId", executionId)
        }

        sock.emit("cancel-command", payload, Ack { args ->
            try {
                val response = args.firstOrNull() as? JSONObject
                if (response != null) {
                    onAck(response.getBoolean("success"), response.optString("error", null))
                } else {
                    onAck(false, ERROR_INVALID_RESPONSE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cancel-command ack", e)
                onAck(false, e.message)
            }
        })
    }

    fun isConnected(): Boolean = socket?.connected() == true

    /**
     * Send PTY input to the backend.
     */
    fun sendPtyInput(executionId: String, data: String, onAck: (PtyAck) -> Unit = {}) {
        val sock = socket
        if (sock == null || !sock.connected()) {
            onAck(PtyAck(success = false, error = ERROR_NOT_CONNECTED))
            return
        }

        val payload = JSONObject().apply {
            put("executionId", executionId)
            put("data", data)
        }

        sock.emit("pty-input", payload, Ack { args ->
            try {
                val response = args.firstOrNull() as? JSONObject
                if (response != null) {
                    onAck(PtyAck(
                        success = response.getBoolean("success"),
                        error = response.optString("error", null)
                    ))
                } else {
                    onAck(PtyAck(success = false, error = ERROR_INVALID_RESPONSE))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing pty-input ack", e)
                onAck(PtyAck(success = false, error = e.message))
            }
        })
    }

    /**
     * Send PTY resize to the backend.
     */
    fun sendPtyResize(executionId: String, cols: Int, rows: Int, onAck: (PtyAck) -> Unit = {}) {
        val sock = socket
        if (sock == null || !sock.connected()) {
            onAck(PtyAck(success = false, error = ERROR_NOT_CONNECTED))
            return
        }

        val payload = JSONObject().apply {
            put("executionId", executionId)
            put("cols", cols)
            put("rows", rows)
        }

        sock.emit("pty-resize", payload, Ack { args ->
            try {
                val response = args.firstOrNull() as? JSONObject
                if (response != null) {
                    onAck(PtyAck(
                        success = response.getBoolean("success"),
                        error = response.optString("error", null)
                    ))
                } else {
                    onAck(PtyAck(success = false, error = ERROR_INVALID_RESPONSE))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing pty-resize ack", e)
                onAck(PtyAck(success = false, error = e.message))
            }
        })
    }

    /**
     * Start an interactive PTY session.
     */
    fun startPty(
        projectId: String,
        sessionId: String?,
        cols: Int,
        rows: Int,
        onAck: (StartPtyAck) -> Unit
    ) {
        val sock = socket
        if (sock == null || !sock.connected()) {
            onAck(StartPtyAck(success = false, executionId = "", error = ERROR_NOT_CONNECTED))
            return
        }

        val payload = JSONObject().apply {
            put("projectId", projectId)
            put("sessionId", sessionId ?: JSONObject.NULL)
            put("cols", cols)
            put("rows", rows)
        }

        sock.emit("start-pty", payload, Ack { args ->
            try {
                val response = args.firstOrNull() as? JSONObject
                if (response != null) {
                    onAck(StartPtyAck(
                        success = response.getBoolean("success"),
                        executionId = response.optString("executionId", ""),
                        error = response.optString("error", null)
                    ))
                } else {
                    onAck(StartPtyAck(success = false, executionId = "", error = ERROR_INVALID_RESPONSE))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing start-pty ack", e)
                onAck(StartPtyAck(success = false, executionId = "", error = e.message))
            }
        })
    }
}
