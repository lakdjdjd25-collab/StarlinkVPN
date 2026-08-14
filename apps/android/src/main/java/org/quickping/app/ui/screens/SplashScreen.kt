package org.quickping.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors

@Composable
fun SplashScreen(ready: Boolean, onFinished: () -> Unit) {
    var minimumDurationElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_650)
        minimumDurationElapsed = true
    }
    LaunchedEffect(ready, minimumDurationElapsed) {
        if (ready && minimumDurationElapsed) onFinished()
    }
    val transition = rememberInfiniteTransition(label = "splashPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "splashAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuickPingColors.Background),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_welcome),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "nimHUB",
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 138.dp, height = 96.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 58.dp)
                .alpha(pulse),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == 1) 5.dp else 3.dp)
                        .background(
                            if (index == 1) QuickPingColors.PrimaryLight else Color(0xFF56657C),
                            CircleShape,
                        ),
                )
                if (index < 2) Spacer(Modifier.width(3.dp))
            }
        }
    }
}
