package com.clauderemote.ui.screens.splash

import android.app.Activity
import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 5500L // 5s animation + 0.5s for swipe up
private val SplashBackground = androidx.compose.ui.graphics.Color(0xFF0A1628)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val view = LocalView.current

    // Set status bar and navigation bar to match splash background
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val originalStatusBarColor = window.statusBarColor
        val originalNavBarColor = window.navigationBarColor

        // Set splash colors
        window.statusBarColor = SplashBackground.toArgb()
        window.navigationBarColor = SplashBackground.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false

        onDispose {
            // Restore original colors when leaving splash
            window.statusBarColor = originalStatusBarColor
            window.navigationBarColor = originalNavBarColor
        }
    }

    // Navigate after splash duration
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true

                    webViewClient = WebViewClient()

                    // Load splash from assets
                    loadUrl("file:///android_asset/splash.html")
                }
            }
        )
    }
}
