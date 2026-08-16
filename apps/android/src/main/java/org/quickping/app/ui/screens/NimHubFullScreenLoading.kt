package org.quickping.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Branded full-screen loading surface used only for app initialization and login bootstrap.
 * The periodic sine wave is continuous at the loop boundary, so the three bars never visibly reset.
 */
@Composable
internal fun NimHubFullScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05070B)),
    ) {
        Image(
            painter = painterResource(R.drawable.nimhub_splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        NimHubLoadingBars(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 118.dp),
        )
    }
}

@Composable
private fun NimHubLoadingBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nimhubLoading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1080, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nimhubLoadingPhase",
    )

    Row(
        modifier = modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val offset = index / 3f
            val wave = ((sin((phase + offset) * 2f * PI).toFloat() + 1f) / 2f)
            val barHeight = 11.dp + 14.dp * wave
            Box(
                Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(
                        Color(0xFFE2E5EC).copy(alpha = 0.5f + 0.5f * wave),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}
