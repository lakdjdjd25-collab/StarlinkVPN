package org.quickping.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun SplashScreen(ready: Boolean, onFinished: () -> Unit) {
    LaunchedEffect(ready) {
        if (ready) onFinished()
    }
    NimHubFullScreenLoading()
}
