package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quickping.app.R
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.Server

@Composable
internal fun ReferenceBestLocationRow(
    selected: Boolean,
    searching: Boolean,
    onClick: () -> Unit,
) {
    val rocketMotion = remember { Animatable(0f) }
    LaunchedEffect(searching) {
        if (!searching) {
            rocketMotion.animateTo(0f, tween(180))
            return@LaunchedEffect
        }
        while (true) {
            rocketMotion.animateTo(1f, tween(360, easing = LinearEasing))
            rocketMotion.animateTo(-1f, tween(360, easing = LinearEasing))
        }
    }
    val motion = if (searching) rocketMotion.value else 0f

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(59.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(ReferenceCardColor)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) QuickPingColors.Primary else ReferenceStrokeColor,
                    RoundedCornerShape(28.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = quickText("بهترین مکان", "Best location"),
                modifier = Modifier.weight(1f),
                color = QuickPingColors.TextPrimary,
                fontFamily = Peyda,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(31.dp), contentAlignment = Alignment.Center) {
                if (searching) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 1.dp, y = (-1).dp)
                            .size(width = 12.dp, height = 8.dp)
                            .blur(5.dp)
                            .background(QuickPingColors.Primary.copy(alpha = 0.30f), CircleShape),
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_rocket),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(29.dp)
                        .graphicsLayer {
                            rotationZ = motion * 2.6f
                            scaleX = 1f + kotlin.math.abs(motion) * 0.025f
                            scaleY = scaleX
                            translationY = motion * 1.5f
                        },
                )
            }
        }
    }
}

@Composable
internal fun ReferenceServerRow(
    server: Server,
    title: String,
    selected: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
) {
    val glowTarget = when {
        !selected -> 0f
        connected -> 1f
        else -> 0.76f
    }
    val glowAlpha by animateFloatAsState(
        targetValue = glowTarget,
        animationSpec = tween(durationMillis = 300),
        label = "serverGlowAlpha",
    )
    val highlightPhase = remember(server.id) { Animatable(0f) }
    LaunchedEffect(selected, server.id) {
        highlightPhase.stop()
        highlightPhase.snapTo(0f)
        if (selected) {
            while (true) {
                highlightPhase.animateTo(
                    targetValue = 960f,
                    animationSpec = tween(durationMillis = 4000, easing = LinearEasing),
                )
                highlightPhase.snapTo(0f)
            }
        }
    }

    val shape = RoundedCornerShape(28.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(59.dp)
                .clip(shape)
                .background(ReferenceCardColor)
                .clickable(enabled = server.selectable, onClick = onClick),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                drawRoundRect(
                    color = if (selected) QuickPingColors.Primary.copy(alpha = 0.74f) else ReferenceStrokeColor,
                    cornerRadius = radius,
                    style = Stroke(width = if (selected) 2.dp.toPx() else 1.dp.toPx()),
                )
                if (glowAlpha > 0.001f) {
                    drawRoundRect(
                        color = QuickPingColors.Primary.copy(alpha = 0.10f * glowAlpha),
                        cornerRadius = radius,
                        style = Stroke(width = 5.dp.toPx()),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = (if (connected) 0.80f else 0.62f) * glowAlpha),
                        cornerRadius = radius,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(46f, 914f),
                                phase = -highlightPhase.value,
                            ),
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 13.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReferenceFlag(
                    server = server,
                    modifier = Modifier
                        .size(width = 45.dp, height = 29.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = if (server.selectable) QuickPingColors.TextPrimary else QuickPingColors.TextMuted,
                    fontFamily = Peyda,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                if (server.unmetered || server.isUnlimitedCategory) {
                    Spacer(Modifier.width(5.dp))
                    ReferenceCapabilityTextBadge("∞", width = 28)
                }
                if (server.isGaming) {
                    Spacer(Modifier.width(5.dp))
                    ReferenceCapabilityIconBadge(R.drawable.ic_game, width = 30)
                }
                if (server.isVip) {
                    Spacer(Modifier.width(5.dp))
                    ReferenceVipBadge()
                }
                Spacer(Modifier.width(7.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ReferencePingChip(server.pingMs)
                }
            }
        }
    }
}

@Composable
private fun ReferenceCapabilityTextBadge(label: String, width: Int) {
    Box(
        modifier = Modifier
            .height(25.dp)
            .width(width.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF171B22))
            .border(1.dp, Color(0xFF303641), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = QuickPingColors.TextSecondary,
            fontFamily = Peyda,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReferenceCapabilityIconBadge(@DrawableRes icon: Int, width: Int) {
    Box(
        modifier = Modifier
            .height(25.dp)
            .width(width.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF171B22))
            .border(1.dp, Color(0xFF303641), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color(0xFF9EA5B4),
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun ReferenceVipBadge() {
    Box(
        modifier = Modifier
            .width(43.dp)
            .height(25.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6655CE),
                        Color(0xFF9A80D2),
                        Color(0xFFD8D985),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "VIP",
            color = Color.White,
            fontFamily = Peyda,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
