package org.quickping.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors

@Composable
fun SplashScreen(ready: Boolean, onFinished: () -> Unit) {
    LaunchedEffect(ready) {
        if (ready) onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuickPingColors.Background),
    ) {
        Image(
            painter = painterResource(R.drawable.nimhub_splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
