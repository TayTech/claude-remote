package com.clauderemote.ui.screens.terminal

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.clauderemote.R
import com.clauderemote.ui.theme.TerminalBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit
) {
    val showBrowser by viewModel.showBrowser.collectAsState()
    val devServerPort by viewModel.devServerPort.collectAsState()
    val serverHost by viewModel.serverHost.collectAsState()
    val terminalSession by viewModel.terminalSession.collectAsState()
    val isCommandRunning by viewModel.isCommandRunning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.terminal_back)
                        )
                    }
                },
                actions = {
                    if (devServerPort != null) {
                        IconButton(onClick = viewModel::toggleBrowser) {
                            Text(
                                text = if (showBrowser) "T" else "B",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A5F), // Dark navy blue
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = TerminalBackground,
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
        ) {

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                showBrowser && devServerPort != null && serverHost.isNotEmpty() -> {
                    BrowserView(
                        url = "http://$serverHost:$devServerPort",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    // Terminal view
                    RemoteTerminalComposable(
                        session = terminalSession,
                        viewModel = viewModel,
                        onSizeChanged = { cols, rows ->
                            viewModel.onTerminalSizeChanged(cols, rows)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Control bar with navigation buttons (enabled only when command is running)
        TerminalControlBar(
            onArrowLeft = { viewModel.sendTerminalInput("\u001B[D") },
            onArrowUp = { viewModel.sendTerminalInput("\u001B[A") },
            onArrowDown = { viewModel.sendTerminalInput("\u001B[B") },
            onArrowRight = { viewModel.sendTerminalInput("\u001B[C") },
            onTab = { viewModel.sendTerminalInput("\t") },
            onEsc = { viewModel.sendTerminalInput("\u001B") },
            onCtrlC = { viewModel.sendTerminalInput("\u0003") },
            onEnter = { viewModel.sendTerminalInput("\r") },
            enabled = isCommandRunning,
            modifier = Modifier.fillMaxWidth()
        )
        }
    }
}

@Composable
private fun RemoteTerminalComposable(
    session: com.clauderemote.data.terminal.RemoteTerminalSession?,
    viewModel: TerminalViewModel,
    onSizeChanged: (cols: Int, rows: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val terminalView = remember { RemoteTerminalView(context) }

    // Track last reported size to avoid redundant calls
    var lastCols = 0
    var lastRows = 0

    // Register view with ViewModel and attach emulator
    DisposableEffect(session) {
        viewModel.setTerminalView(terminalView)
        session?.let { sess ->
            terminalView.attachEmulator(sess.emulator)
        }
        onDispose {
            viewModel.setTerminalView(null)
        }
    }

    AndroidView(
        factory = {
            terminalView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setTextSize(28)
                setTypeface(Typeface.MONOSPACE)
                onInput = { data -> viewModel.sendTerminalInput(data) }

                // Add layout change listener to detect keyboard show/hide
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    post {
                        val cols = getVisibleColumns()
                        val rows = getVisibleRows()
                        if (cols > 0 && rows > 0 && (cols != lastCols || rows != lastRows)) {
                            lastCols = cols
                            lastRows = rows
                            onSizeChanged(cols, rows)
                        }
                    }
                }
            }
        },
        update = { view ->
            // Report size changes
            view.post {
                val cols = view.getVisibleColumns()
                val rows = view.getVisibleRows()
                if (cols > 0 && rows > 0 && (cols != lastCols || rows != lastRows)) {
                    lastCols = cols
                    lastRows = rows
                    onSizeChanged(cols, rows)
                }
            }
        },
        modifier = modifier.background(Color(0xFF1E1E1E))
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserView(
    url: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        view?.loadData(
                            "<html><body><h3>Error loading page</h3><p>$description</p></body></html>",
                            "text/html",
                            "UTF-8"
                        )
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    safeBrowsingEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier
    )
}
