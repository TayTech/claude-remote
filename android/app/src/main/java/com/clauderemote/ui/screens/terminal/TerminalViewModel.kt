package com.clauderemote.ui.screens.terminal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clauderemote.data.repository.ClaudeRemoteRepository
import com.clauderemote.data.terminal.RemoteTerminalSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TerminalViewModel"

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val repository: ClaudeRemoteRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_COLS = 80
        private const val DEFAULT_ROWS = 24
    }

    // UI State
    private val _showBrowser = MutableStateFlow(false)
    val showBrowser: StateFlow<Boolean> = _showBrowser.asStateFlow()

    private val _devServerPort = MutableStateFlow<Int?>(null)
    val devServerPort: StateFlow<Int?> = _devServerPort.asStateFlow()

    private val _serverHost = MutableStateFlow("")
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    // True when PTY is connected and ready
    private val _isCommandRunning = MutableStateFlow(false)
    val isCommandRunning: StateFlow<Boolean> = _isCommandRunning.asStateFlow()

    // Terminal session
    private val _terminalSession = MutableStateFlow<RemoteTerminalSession?>(null)
    val terminalSession: StateFlow<RemoteTerminalSession?> = _terminalSession.asStateFlow()

    // Internal state
    private var projectId: String = ""
    private var sessionId: String? = null
    private var currentExecutionId: String? = null
    private var currentCols = DEFAULT_COLS
    private var currentRows = DEFAULT_ROWS
    private var isInitialized = false

    // Reference to terminal view for updates
    private var terminalView: RemoteTerminalView? = null

    fun initialize(projectId: String, sessionId: String?) {
        // Prevent re-initialization on configuration changes (rotation)
        if (isInitialized) {
            Log.d(TAG, "Already initialized, skipping re-initialization")
            return
        }
        isInitialized = true

        this.projectId = projectId
        this.sessionId = sessionId

        viewModelScope.launch {
            // Load project info
            val result = repository.getProjects()
            result.onSuccess { projects ->
                val project = projects.find { it.id == projectId }
                _devServerPort.value = project?.devServerPort
            }

            // Load server host
            val settings = repository.getServerSettings()
            _serverHost.value = settings?.host ?: ""

            // Subscribe to PTY output
            launch {
                repository.outputChunks.collect { chunk ->
                    if (chunk.executionId == currentExecutionId) {
                        _terminalSession.value?.feedRemoteData(chunk.content)
                        terminalView?.onScreenUpdated()
                    }
                }
            }

            // Handle session completion (user exits Claude CLI)
            launch {
                repository.commandComplete.collect { complete ->
                    if (complete.executionId == currentExecutionId) {
                        Log.d(TAG, "Interactive session ended")
                        _isCommandRunning.value = false
                        currentExecutionId = null
                        // Restart interactive session
                        startInteractiveSession()
                    }
                }
            }

            // Handle errors
            launch {
                repository.commandError.collect { error ->
                    if (error.executionId == currentExecutionId) {
                        Log.e(TAG, "PTY error: ${error.error}")
                        _isCommandRunning.value = false
                        currentExecutionId = null
                        _terminalSession.value?.feedRemoteData("\r\n\u001B[31mError: ${error.error}\u001B[0m\r\n")
                        terminalView?.onScreenUpdated()
                        // Restart interactive session
                        startInteractiveSession()
                    }
                }
            }

            // Create terminal session for display
            createTerminalSession()

            // Start interactive PTY immediately
            startInteractiveSession()
        }
    }

    private fun startInteractiveSession() {
        Log.d(TAG, "Starting interactive PTY session")

        repository.startPty(projectId, sessionId, currentCols, currentRows) { ack ->
            viewModelScope.launch {
                if (ack.success) {
                    currentExecutionId = ack.executionId
                    _isCommandRunning.value = true
                    Log.d(TAG, "Interactive PTY started: ${ack.executionId}")
                } else {
                    Log.e(TAG, "Failed to start interactive PTY: ${ack.error}")
                    _terminalSession.value?.feedRemoteData("\r\n\u001B[31mFailed to connect: ${ack.error}\u001B[0m\r\n")
                    terminalView?.onScreenUpdated()
                }
            }
        }
    }

    fun setTerminalView(view: RemoteTerminalView?) {
        terminalView = view
    }

    /**
     * Send input directly to the remote PTY.
     * All keystrokes go straight to Claude CLI for real-time interaction.
     */
    fun sendTerminalInput(data: String) {
        Log.d(TAG, "sendTerminalInput: '${data.take(10)}' (len=${data.length})")

        val executionId = currentExecutionId
        if (executionId != null) {
            // Send directly to PTY - Claude CLI handles everything
            repository.sendPtyInput(executionId, data)
        } else {
            Log.w(TAG, "No active PTY session, input ignored")
        }
    }

    private fun createTerminalSession() {
        Log.d(TAG, "Creating terminal session")
        try {
            val client = createTerminalSessionClient()
            val session = RemoteTerminalSession(
                executionId = "local",
                onInput = { data ->
                    currentExecutionId?.let { execId ->
                        repository.sendPtyInput(execId, data)
                    }
                },
                onResize = { cols, rows ->
                    currentExecutionId?.let { execId ->
                        repository.sendPtyResize(execId, cols, rows)
                    }
                },
                client = client,
                columns = currentCols,
                rows = currentRows,
                onScreenUpdate = {
                    terminalView?.onScreenUpdated()
                }
            )

            _terminalSession.value = session
            Log.d(TAG, "Terminal session created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create terminal session", e)
        }
    }

    private fun createTerminalSessionClient(): TerminalSessionClient {
        return object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession?) {
                terminalView?.onScreenUpdated()
            }
            override fun onTitleChanged(changedSession: TerminalSession?) {}
            override fun onSessionFinished(finishedSession: TerminalSession?) {}
            override fun onBell(session: TerminalSession?) {}
            override fun onColorsChanged(session: TerminalSession?) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
            override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        }
    }

    fun onTerminalSizeChanged(cols: Int, rows: Int) {
        if (cols == currentCols && rows == currentRows) return

        currentCols = cols
        currentRows = rows

        _terminalSession.value?.updateSize(cols, rows)
        currentExecutionId?.let { execId ->
            repository.sendPtyResize(execId, cols, rows)
        }
    }

    fun toggleBrowser() {
        _showBrowser.value = !_showBrowser.value
    }
}
