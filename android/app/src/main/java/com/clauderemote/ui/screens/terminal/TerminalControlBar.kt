package com.clauderemote.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Control bar with navigation buttons for terminal interaction.
 * Provides arrow keys, Tab, Esc, and Ctrl+C buttons for mobile-friendly navigation.
 * Left/Right arrows are always enabled (for cursor movement), others only when command is running.
 * Second row contains the Enter button for submitting input.
 */
@Composable
fun TerminalControlBar(
    onArrowLeft: () -> Unit,
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    onArrowRight: () -> Unit,
    onTab: () -> Unit,
    onEsc: () -> Unit,
    onCtrlC: () -> Unit,
    onEnter: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
    ) {
        // First row: Navigation keys
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Arrow Left - always enabled for cursor movement
            ControlButton(text = "←", onClick = onArrowLeft, enabled = true)

            // Arrow Up - only when command running
            ControlButton(text = "↑", onClick = onArrowUp, enabled = enabled)

            // Arrow Down - only when command running
            ControlButton(text = "↓", onClick = onArrowDown, enabled = enabled)

            // Arrow Right - always enabled for cursor movement
            ControlButton(text = "→", onClick = onArrowRight, enabled = true)

            // Tab - only when command running
            ControlButton(text = "Tab", onClick = onTab, enabled = enabled, width = 52.dp)

            // Escape - only when command running
            ControlButton(text = "Esc", onClick = onEsc, enabled = enabled, width = 52.dp)

            // Ctrl+C - only when command running
            ControlButton(
                text = "🚫",
                onClick = onCtrlC,
                enabled = enabled
            )
        }

        // Second row: Enter button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enter button - prominent, always enabled
            Button(
                onClick = onEnter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E5631) // Dark green
                )
            ) {
                Text(
                    text = "⏎  ENTER",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color(0xFF404040),
    width: Dp = 48.dp
) {
    val actualBackgroundColor = if (enabled) backgroundColor else Color(0xFF2A2A2A)
    val textColor = if (enabled) Color.White else Color(0xFF666666)

    Button(
        onClick = onClick,
        modifier = modifier
            .size(width = width, height = 44.dp),
        shape = RoundedCornerShape(6.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = actualBackgroundColor,
            disabledContainerColor = actualBackgroundColor
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
