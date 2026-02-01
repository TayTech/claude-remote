package com.clauderemote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clauderemote.ui.navigation.ClaudeRemoteNavGraph
import com.clauderemote.ui.theme.ClaudeRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaudeRemoteTheme {
                ClaudeRemoteNavGraph()
            }
        }
    }
}
