package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import org.quickping.app.R
import org.quickping.app.core.design.Bitcount
import org.quickping.app.core.design.MonaSans
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.model.selectBestServerForAuto
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun ReferenceHomeScreen(
    state: QuickPingUiState,
    onToggleConnection: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val connected = state.connectionStatus == ConnectionStatus.Connected
    val pendingReview = state.service.pendingReview
    val toggleConnection: () -> Unit = if (pendingReview) ({}) else onToggleConnection

    Box(Modifier.fillMaxSize().background(QuickPingColors.Background)) {
        Image(
            painter = painterResource(if (connected) R.drawable.bg_home_connected else R.drawable.bg_home_disconnected),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(318.dp),
            contentScale = ContentScale.FillBounds,
        )

        ReferenceConnectedRingGlow(
            connectionStatus = state.connectionStatus,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(286.dp),
        )
        // Geometry remains the exact static NimHUB ring asset in every connection state.
        Image(
            painter = painterResource(R.drawable.circles_disconnected),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(286.dp),
            contentScale = ContentScale.Fit,
        )

        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(324.dp).background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .76f to Color.Transparent,
                        1f to QuickPingColors.Background,
                    ),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ReferenceHomeHeader(state, onSettings, onAccount, onNotifications, onUpgrade)
            ReferenceConnectionPanel(state, false, toggleConnection)
            ReferenceServerList(
                modifier = Modifier.weight(1f).background(QuickPingColors.Background),
                state = state,
                bestLocationSelected = false,
                onSelectBestLocation = {
                    val best = selectBestServerForAuto(state.servers)
                    best?.let { server ->
                        onSelectServer(server.id)
                        if (state.connectionStatus !in setOf(ConnectionStatus.Connected, ConnectionStatus.Connecting)) {
                            onToggleConnection()
                        }
                    }
                },
                onSelectServer = onSelectServer,
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(50.dp).background(
                Brush.verticalGradient(listOf(Color.Transparent, QuickPingColors.Background.copy(alpha = .99f))),
            ),
        )
    }
}

@Composable
private fun ReferenceConnectedRingGlow(
    connectionStatus: ConnectionStatus,
    modifier: Modifier,
) {
    val visibleTarget = if (
        connectionStatus == ConnectionStatus.Connecting || connectionStatus == ConnectionStatus.Connected
    ) 1f else 0f
    val visibility by animateFloatAsState(
        targetValue = visibleTarget,
        animationSpec = tween(durationMillis = 420),
        label = "headerRingGlowVisibility",
    )
    val phase = remember { Animatable(0f) }
    LaunchedEffect(connectionStatus) {
        phase.stop()
        phase.snapTo(0f)
        if (connectionStatus == ConnectionStatus.Connected) {
            while (true) {
                phase.animateTo(1f, tween(3200, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }

    if (visibility <= 0.001f) return
    Canvas(modifier) {
        // circles_disconnected.xml uses a 342x342 viewport. These exact centers/radii preserve geometry.
        val side = min(size.width, size.height)
        val scale = side / 342f
        val originX = (size.width - side) / 2f
        val originY = (size.height - side) / 2f
        val rings = listOf(
            Triple(171f, 171f, 170.4f),
            Triple(171f, 171f, 136.4f),
            Triple(170.5f, 166f, 98.4f),
        )
        val offsets = floatArrayOf(0f, 0.047f, 0.094f)
        rings.forEachIndexed { index, (cx, cy, radius) ->
            val pulse = if (connectionStatus == ConnectionStatus.Connected) {
                ((sin(2.0 * PI * (phase.value + offsets[index])).toFloat() + 1f) / 2f)
            } else {
                0.18f
            }
            val alpha = visibility * (0.055f + pulse * 0.045f)
            val center = Offset(originX + cx * scale, originY + cy * scale)
            val scaledRadius = radius * scale
            drawCircle(
                color = QuickPingColors.Primary.copy(alpha = alpha * 0.34f),
                radius = scaledRadius,
                center = center,
                style = Stroke(width = 7.dp.toPx()),
            )
            drawCircle(
                color = QuickPingColors.Primary.copy(alpha = alpha),
                radius = scaledRadius,
                center = center,
                style = Stroke(width = 2.2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ReferenceHomeHeader(
    state: QuickPingUiState,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
    onUpgrade: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(154.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReferenceCircleButton(R.drawable.ic_user, onAccount)
                Spacer(Modifier.width(7.dp))
                ReferencePlanPill(state.service.isFree, onUpgrade)
                Spacer(Modifier.weight(1f))
                ReferenceNotificationButton(
                    unreadCount = state.notifications.count { !it.read },
                    onClick = onNotifications,
                )
                Spacer(Modifier.width(7.dp))
                ReferenceCircleButton(R.drawable.ic_settings, onSettings)
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.home_title_start), null, Modifier.size(33.dp, 15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "NIMHUBPING",
                        color = Color.White,
                        fontFamily = MonaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        lineHeight = 34.sp,
                        letterSpacing = (-1.05).sp,
                    )
                    Spacer(Modifier.width(3.dp))
                    Image(painterResource(R.drawable.home_title_end), null, Modifier.size(33.dp, 15.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (state.service.pendingReview) quickText(
                    "در حال بررسی", "Under review", nl = "Wordt beoordeeld", ar = "قيد المراجعة",
                    tr = "İnceleniyor", ru = "На проверке", hi = "समीक्षा में", zh = "审核中", ur = "زیرِ جائزہ",
                ) else when (state.connectionStatus) {
                    ConnectionStatus.Connected -> quickText("متصل است", "is Connected")
                    ConnectionStatus.Connecting -> quickText("در حال اتصال", "is Connecting")
                    ConnectionStatus.Error -> quickText("خطای اتصال", "Connection Error")
                    ConnectionStatus.Disconnected -> quickText("قطع است", "is Disconnected")
                },
                color = Color(0xFF8B8D94),
                fontFamily = Bitcount,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                letterSpacing = .32.sp,
            )
        }
    }
}

@Composable
private fun ReferencePlanPill(isFree: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(35.dp).width(if (isFree) 56.dp else 67.dp).clip(CircleShape).then(
            if (isFree) Modifier.background(QuickPingColors.Surface.copy(alpha = .82f)).border(1.dp, Color(0xFF3E4654), CircleShape)
            else Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF6555CE), Color(0xFFA28DCE), Color(0xFFD3D884))))
        ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isFree) quickText("رایگان", "Free") else quickText("ارتقا", "Upgrade"),
            color = if (isFree) QuickPingColors.TextSecondary else Color.White,
            fontFamily = Peyda,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReferenceNotificationButton(unreadCount: Int, onClick: () -> Unit) {
    val bellRotation = remember { Animatable(0f) }
    val badgeScale = remember { Animatable(1f) }
    var previousUnread by remember { mutableStateOf(unreadCount) }

    LaunchedEffect(unreadCount) {
        if (unreadCount > previousUnread) {
            bellRotation.stop()
            bellRotation.snapTo(0f)
            bellRotation.animateTo(-6f, tween(110))
            bellRotation.animateTo(7f, tween(150))
            bellRotation.animateTo(-4f, tween(130))
            bellRotation.animateTo(0f, tween(130))

            badgeScale.stop()
            badgeScale.snapTo(0.70f)
            badgeScale.animateTo(1.05f, tween(130))
            badgeScale.animateTo(1f, tween(120))
        }
        previousUnread = unreadCount
    }

    Box(
        Modifier
            .size(35.dp)
            .clip(CircleShape)
            .background(Color(0xFF17191F).copy(alpha = .95f))
            .border(1.dp, Color(0xFF20242C), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bell),
            contentDescription = quickText("اعلان‌ها", "Notifications"),
            tint = QuickPingColors.TextSecondary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = bellRotation.value },
        )
        if (unreadCount > 0) {
            val countLabel = if (unreadCount > 99) "99+" else unreadCount.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .height(14.dp)
                    .width(if (unreadCount > 9) 20.dp else 14.dp)
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                    }
                    .background(Color(0xFFE34D5B), CircleShape)
                    .border(1.dp, Color(0xFF17191F), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = countLabel,
                    color = Color.White,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ReferenceCircleButton(@DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(35.dp).clip(CircleShape).background(Color(0xFF17191F).copy(alpha = .95f))
            .border(1.dp, Color(0xFF20242C), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = QuickPingColors.TextSecondary, modifier = Modifier.size(16.dp))
    }
}
