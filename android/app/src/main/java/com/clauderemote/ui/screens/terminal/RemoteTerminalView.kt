package com.clauderemote.ui.screens.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import com.termux.terminal.TerminalEmulator
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View that renders a TerminalEmulator's output and handles keyboard input.
 * Designed for remote PTY sessions without requiring TerminalSession.
 */
class RemoteTerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "RemoteTerminalView"
        private const val DEFAULT_FONT_SIZE = 28
    }

    // Terminal emulator reference
    private var emulator: TerminalEmulator? = null

    // Callback for sending input to remote PTY
    var onInput: ((String) -> Unit)? = null

    // Paint for text rendering
    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textSize = DEFAULT_FONT_SIZE.toFloat()
    }

    // Font metrics
    private var fontWidth: Float = 0f
    private var fontLineSpacing: Int = 0
    private var fontAscent: Int = 0

    // Colors (standard terminal colors)
    private val defaultForeground = 0xFFFFFFFF.toInt()
    private val defaultBackground = 0xFF1E1E1E.toInt()

    // Standard ANSI colors
    private val colorPalette = intArrayOf(
        0xFF000000.toInt(), // 0: Black
        // 0xFFCD0000.toInt(), // 1: Red
        0xFF00CD00.toInt(), // 1: Green
        0xFF00CD00.toInt(), // 2: Green
        0xFFCDCD00.toInt(), // 3: Yellow
        0xFF0000EE.toInt(), // 4: Blue
        0xFFCD00CD.toInt(), // 5: Magenta
        0xFF00CDCD.toInt(), // 6: Cyan
        0xFFE5E5E5.toInt(), // 7: White
        0xFFCCCCCC.toInt(), // 8: Bright Black (light gray for visibility)
        0xFFFF0000.toInt(), // 9: Bright Red
        0xFF00FF00.toInt(), // 10: Bright Green
        0xFFFFFF00.toInt(), // 11: Bright Yellow
        0xFF5C5CFF.toInt(), // 12: Bright Blue
        0xFFFF00FF.toInt(), // 13: Bright Magenta
        0xFF00FFFF.toInt(), // 14: Bright Cyan
        0xFFFFFFFF.toInt()  // 15: Bright White
    )

    // Scrolling
    private var topRow: Int = 0
    private val scroller = OverScroller(context)
    private var lastTouchY: Float = 0f

    // Gesture detector for scrolling and taps
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // Must return true for other gestures to work
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Log.d(TAG, "onSingleTapUp - requesting focus and keyboard")
            requestFocus()
            showKeyboard()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            doScroll(distanceY.toInt())
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val em = emulator ?: return false
            val maxScrollBack = em.screen.activeTranscriptRows
            val totalRows = em.mRows
            val visibleRows = getVisibleRows()
            val maxTopRow = max(0, totalRows - visibleRows)
            scroller.fling(0, topRow, 0, (-velocityY / fontLineSpacing).toInt(), 0, 0, -maxScrollBack, maxTopRow)
            invalidate()
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateFontMetrics()
    }

    private fun updateFontMetrics() {
        fontWidth = textPaint.measureText("X")
        val metrics = textPaint.fontMetrics
        fontAscent = (-metrics.ascent).toInt()
        fontLineSpacing = (metrics.descent - metrics.ascent + metrics.leading).toInt()
    }

    fun setTextSize(size: Int) {
        textPaint.textSize = size.toFloat()
        updateFontMetrics()
        invalidate()
    }

    fun setTypeface(typeface: Typeface) {
        textPaint.typeface = typeface
        updateFontMetrics()
        invalidate()
    }

    /**
     * Attach a TerminalEmulator to this view for rendering.
     */
    fun attachEmulator(emulator: TerminalEmulator) {
        this.emulator = emulator
        topRow = 0
        invalidate()
    }

    /**
     * Notify that the emulator content has changed.
     */
    fun onScreenUpdated() {
        // Auto-scroll to cursor position
        val em = emulator ?: return
        val cursorRow = em.cursorRow
        val visibleRows = getVisibleRows()
        val totalRows = em.mRows

        Log.d(TAG, "onScreenUpdated: cursor=$cursorRow, topRow=$topRow, visibleRows=$visibleRows, totalRows=$totalRows")

        // Calculate proper scroll position to keep cursor visible
        // topRow is the first row to display (0 = first row of active screen, negative = transcript)
        // cursorRow is relative to active screen (0 to mRows-1)

        // If cursor is above visible area, scroll up
        if (cursorRow < topRow) {
            topRow = cursorRow
        }
        // If cursor is below visible area, scroll down
        else if (cursorRow >= topRow + visibleRows) {
            topRow = cursorRow - visibleRows + 1
        }

        // Keep topRow in valid range
        val maxScrollBack = em.screen.activeTranscriptRows
        val maxTopRow = max(0, totalRows - visibleRows) // Can't scroll past last row
        topRow = topRow.coerceIn(-maxScrollBack, maxTopRow)

        invalidate()
    }

    /**
     * Scroll to show the cursor (useful when keyboard appears).
     */
    fun scrollToCursor() {
        onScreenUpdated()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(TAG, "onSizeChanged: ${oldw}x${oldh} -> ${w}x${h}")
        // When view size changes (keyboard appears/disappears), scroll to keep cursor visible
        // Use postDelayed to ensure the new size is fully applied
        postDelayed({
            scrollToCursor()
            // Request layout to ensure proper rendering
            requestLayout()
        }, 50)
    }

    /**
     * Calculate visible columns based on view width.
     */
    fun getVisibleColumns(): Int {
        return if (fontWidth > 0 && width > 0) {
            (width / fontWidth).toInt().coerceAtLeast(10)
        } else 80
    }

    /**
     * Calculate visible rows based on view height.
     */
    fun getVisibleRows(): Int {
        return if (fontLineSpacing > 0 && height > 0) {
            (height / fontLineSpacing).coerceAtLeast(5)
        } else 24
    }

    private fun doScroll(deltaY: Int) {
        val em = emulator ?: return
        val rowDelta = deltaY / fontLineSpacing.coerceAtLeast(1)
        val maxScrollBack = em.screen.activeTranscriptRows
        val totalRows = em.mRows
        val visibleRows = getVisibleRows()
        val maxTopRow = max(0, totalRows - visibleRows) // Can't scroll past last row
        topRow = (topRow + rowDelta).coerceIn(-maxScrollBack, maxTopRow)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawColor(defaultBackground)

        val em = emulator ?: return
        val screen = em.screen

        val visibleRows = getVisibleRows()
        val visibleCols = getVisibleColumns()
        val totalRows = em.mRows

        // Track the last row with '>' for cursor positioning
        var lastPromptRow = -1
        var isPromptReset = false

        // Draw each visible row with proper styling
        for (viewRow in 0 until min(visibleRows, totalRows)) {
            val emulatorRow = topRow + viewRow
            if (emulatorRow < -screen.activeTranscriptRows || emulatorRow >= totalRows) continue

            val y = fontAscent + viewRow * fontLineSpacing

            // Get the line text
            val lineText = try {
                screen.getSelectedText(0, emulatorRow, visibleCols - 1, emulatorRow) ?: ""
            } catch (e: Exception) {
                ""
            }

            if (lineText.isEmpty()) continue

            // Check if this line contains '>' at the beginning (input prompt)
            // The prompt line format is: ">Try..." or similar
            val trimmed = lineText.trimStart()
            if (trimmed.startsWith("❯") && !trimmed.startsWith(">>")) {
                lastPromptRow = viewRow
            }

            // Check style of first non-space character to determine line styling
            var lineIsDim = false
            var lineIsBold = false
            var lineColor = defaultForeground

            for (col in 0 until min(lineText.length, visibleCols)) {
                val style = try {
                    screen.getStyleAt(emulatorRow, col)
                } catch (e: Exception) {
                    0L
                }

                val fg = (style and 0x1FF).toInt()
                val effect = ((style shr 18) and 0xFF).toInt()

                // Check for dim/faint attribute (bit 1 of effects)
                if ((effect and 0x02) != 0) lineIsDim = true
                // Check for bold attribute (bit 0 of effects)
                if ((effect and 0x01) != 0) lineIsBold = true

                // Get foreground color from first styled character
                if (fg > 0 && fg < 256 && lineColor == defaultForeground) {
                    lineColor = getColorFromPalette(fg)

                    // if is cursor placeholder row
                    if (lineColor == -5592406) {
                        isPromptReset = true
                    }
                }

                // Stop after finding style info from first few characters
                if (col > 5) break
            }

            // Apply dim effect if detected - use light gray for readability
            if (lineIsDim) {
                lineColor = 0xFFAAAAAA.toInt()
            }

            // Draw the entire line with detected style
            textPaint.color = lineColor
            textPaint.isFakeBoldText = lineIsBold
            canvas.drawText(lineText, 0f, y.toFloat(), textPaint)

            // Reset paint
            textPaint.isFakeBoldText = false
        }

        // Draw cursor on the prompt line (line starting with ">")
        // Position it right after the ">" character
        if (lastPromptRow >= 0) {
            // Get the text on the prompt line to calculate cursor position
            val emulatorRow = topRow + lastPromptRow
            val promptLineText = try {
                screen.getSelectedText(0, emulatorRow, visibleCols - 1, emulatorRow) ?: ""
            } catch (e: Exception) { "" }

            // Cursor at the end of the text (after last non-space character)
            val cursorCol = promptLineText.trimEnd().length
            // lineText.indexOf('❯') + 1
            var cursorX = cursorCol * fontWidth
            val cursorY = lastPromptRow * fontLineSpacing

            // Draw cursor block with light gray color
            if(isPromptReset) {
                textPaint.color = 0x55FFFFFF.toInt()
                cursorX = (2 * fontWidth) - 3
            }
            // canvas.drawText("TXT: "+isPromptReset, 0f, ((em.cursorRow - topRow) * fontLineSpacing).toFloat() , textPaint)
            canvas.drawRect(
                cursorX,
                cursorY.toFloat(),
                cursorX + fontWidth,
                (cursorY + fontLineSpacing).toFloat(),
                textPaint
            )
        }
    }

    /**
     * Get color from the 256-color palette.
     */
    private fun getColorFromPalette(index: Int): Int {
        return when {
            index < 16 -> colorPalette.getOrElse(index) { defaultForeground }
            index < 232 -> {
                // 216 colors: 6x6x6 cube
                val i = index - 16
                val r = (i / 36) * 51
                val g = ((i / 6) % 6) * 51
                val b = 0xAAAAAA
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            else -> {
                // 24 grayscale colors
                val gray = (index - 232) * 10 + 8
                (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            topRow = scroller.currY
            invalidate()
        }
    }

    private fun showKeyboard() {
        Log.d(TAG, "showKeyboard called, view has focus: ${hasFocus()}")
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Use SHOW_IMPLICIT for more reliable keyboard display
        val result = imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        Log.d(TAG, "showSoftInput result: $result")
    }

    // Input handling
    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Disable autocomplete, suggestions, and spell check for raw character input
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or
            EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return TerminalInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val char = event.unicodeChar
        if (char != 0) {
            sendInput(char.toChar().toString())
            return true
        }

        // Handle special keys
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                sendInput("\r")
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                sendInput("\u007F") // DEL character
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                sendInput("\t")
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                sendInput("\u001B")
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                sendInput("\u001B[A")
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                sendInput("\u001B[B")
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                sendInput("\u001B[C")
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                sendInput("\u001B[D")
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    fun sendInput(data: String) {
        Log.d(TAG, "sendInput: ${data.take(20)}")
        onInput?.invoke(data)
    }

    /**
     * Send a control character (Ctrl+key).
     */
    fun sendControlKey(key: Char) {
        val code = key.uppercaseChar().code - 64
        if (code in 0..31) {
            sendInput(code.toChar().toString())
        }
    }

    /**
     * InputConnection for handling soft keyboard input.
     * Sends characters directly to PTY without composing text logic.
     */
    private inner class TerminalInputConnection(view: View) : BaseInputConnection(view, false) {
        private var lastComposingLength = 0

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            Log.d(TAG, "setComposingText: '$text' (last=$lastComposingLength)")
            // Only send new characters (delta from previous composing text)
            val newLength = text.length
            if (newLength > lastComposingLength) {
                // New characters added - send only the new ones
                val delta = text.subSequence(lastComposingLength, newLength)
                sendInput(delta.toString())
            } else if (newLength < lastComposingLength) {
                // Characters deleted - send backspaces
                repeat(lastComposingLength - newLength) {
                    sendInput("\u007F")
                }
            }
            lastComposingLength = newLength
            return true
        }

        override fun finishComposingText(): Boolean {
            Log.d(TAG, "finishComposingText")
            lastComposingLength = 0
            return true
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            Log.d(TAG, "commitText: '$text' (lastComposing=$lastComposingLength)")
            // If we have composing text, we already sent it character by character
            // Just reset and handle any final text not yet sent
            if (lastComposingLength == 0 && text.isNotEmpty()) {
                // Direct commit without composing - send the text
                sendInput(text.toString())
            }
            // For Enter key, Samsung sends "\n" via commitText
            if (text == "\n") {
                sendInput("\r")
            }
            lastComposingLength = 0
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            Log.d(TAG, "deleteSurroundingText: before=$beforeLength after=$afterLength")
            repeat(beforeLength) {
                sendInput("\u007F")
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            Log.d(TAG, "sendKeyEvent: ${event.keyCode} action=${event.action}")
            if (event.action == KeyEvent.ACTION_DOWN) {
                return onKeyDown(event.keyCode, event)
            }
            return super.sendKeyEvent(event)
        }
    }
}
