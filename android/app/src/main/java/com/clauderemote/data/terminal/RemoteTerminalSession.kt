package com.clauderemote.data.terminal

import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.nio.charset.StandardCharsets

/**
 * RemoteTerminalSession bridges the Termux TerminalEmulator to a remote PTY via Socket.io.
 * Instead of a local shell, this connects to a backend PTY process.
 */
class RemoteTerminalSession(
    private val executionId: String,
    private val onInput: (String) -> Unit,
    private val onResize: (cols: Int, rows: Int) -> Unit,
    private val client: TerminalSessionClient,
    columns: Int = 80,
    rows: Int = 24,
    transcriptRows: Int = 2000,
    private val onScreenUpdate: (() -> Unit)? = null
) : TerminalOutput() {

    companion object {
        private const val TAG = "RemoteTerminalSession"

        // Precompiled regex patterns for filtering unsupported escape sequences
        private val EXTENDED_MOUSE_MODE = Regex("\u001B\\[<u")
        private val SYNCHRONIZED_UPDATE = Regex("\u001B\\[\\?2026[hl]")
        private val ITERM2_PROPRIETARY = Regex("\u001B]9;[^\u0007]*\u0007")
        private val KITTY_KEYBOARD_GT = Regex("\u001B\\[>u")
        private val KITTY_KEYBOARD_LT = Regex("\u001B\\[<[0-9;]*u")
    }

    val emulator: TerminalEmulator

    // Track current size
    private var currentCols = columns
    private var currentRows = rows

    init {
        emulator = TerminalEmulator(
            this,
            columns,
            rows,
            transcriptRows,
            client
        )
        Log.d(TAG, "RemoteTerminalSession created for $executionId, size: ${columns}x${rows}")
    }

    /**
     * Feed data received from the remote PTY into the terminal emulator.
     * This processes ANSI escape sequences and updates the terminal display.
     */
    fun feedRemoteData(data: String) {
        // Filter out escape sequences that the emulator doesn't understand
        // These would otherwise render as garbage characters (like "uu")
        val filtered = filterUnsupportedSequences(data)
        if (filtered.isEmpty()) return

        val bytes = filtered.toByteArray(StandardCharsets.UTF_8)
        emulator.append(bytes, bytes.size)
        notifyScreenUpdate()
    }

    /**
     * Filter out escape sequences that the Termux emulator doesn't support.
     * These sequences would otherwise render as garbage text.
     */
    private fun filterUnsupportedSequences(data: String): String {
        var result = data

        // Extended mouse mode sequences (CSI < ... ) - renders as "uu" or similar
        // Pattern: ESC [ < followed by various commands including 'u'
        result = result.replace(EXTENDED_MOUSE_MODE, "")

        // Synchronized update mode (not widely supported)
        result = result.replace(SYNCHRONIZED_UPDATE, "")

        // iTerm2 proprietary sequences (OSC 9)
        result = result.replace(ITERM2_PROPRIETARY, "")

        // Kitty keyboard protocol (CSI > u and CSI < u)
        result = result.replace(KITTY_KEYBOARD_GT, "")
        result = result.replace(KITTY_KEYBOARD_LT, "")

        return result
    }

    /**
     * Feed raw bytes from the remote PTY into the terminal emulator.
     */
    fun feedRemoteData(data: ByteArray) {
        emulator.append(data, data.size)
        notifyScreenUpdate()
    }

    /**
     * Resize the terminal emulator and notify the remote PTY.
     */
    fun updateSize(cols: Int, rows: Int) {
        if (cols == currentCols && rows == currentRows) return

        currentCols = cols
        currentRows = rows
        emulator.resize(cols, rows)
        onResize(cols, rows)
        Log.d(TAG, "Terminal resized to ${cols}x${rows}")
    }

    /**
     * Get current column count.
     */
    fun getCols(): Int = currentCols

    /**
     * Get current row count.
     */
    fun getRows(): Int = currentRows

    // TerminalOutput implementation

    /**
     * Called by TerminalEmulator when it writes output.
     * In a local terminal this would go to the shell process.
     * For remote, we send it to the backend PTY.
     */
    override fun write(data: ByteArray, offset: Int, count: Int) {
        val str = String(data, offset, count, StandardCharsets.UTF_8)
        Log.d(TAG, "Terminal output (to remote): ${str.take(100)}")
        onInput(str)
    }

    /**
     * Called to notify title changes.
     */
    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        Log.d(TAG, "Title changed: $newTitle")
        client.onTitleChanged(null)
    }

    /**
     * Called when text should be copied to clipboard.
     */
    override fun onCopyTextToClipboard(text: String?) {
        text?.let {
            client.onCopyTextToClipboard(null, it)
        }
    }

    /**
     * Called when text should be pasted from clipboard.
     */
    override fun onPasteTextFromClipboard() {
        client.onPasteTextFromClipboard(null)
    }

    /**
     * Called when bell character is received.
     */
    override fun onBell() {
        client.onBell(null)
    }

    /**
     * Called when colors change.
     */
    override fun onColorsChanged() {
        client.onColorsChanged(null)
    }

    private fun notifyScreenUpdate() {
        client.onTextChanged(null)
        onScreenUpdate?.invoke()
    }

    /**
     * Get the execution ID this session is connected to.
     */
    fun getExecutionId(): String = executionId

    /**
     * Send a special key or control sequence to the remote PTY.
     */
    fun writeControlKey(key: Int) {
        val data = byteArrayOf(key.toByte())
        onInput(String(data, StandardCharsets.UTF_8))
    }

    /**
     * Cleanup resources.
     */
    fun finish() {
        Log.d(TAG, "RemoteTerminalSession finished for $executionId")
    }
}
