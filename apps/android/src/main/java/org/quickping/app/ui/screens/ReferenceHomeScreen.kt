package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quickping.app.R
import org.quickping.app.core.design.*
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
        Image(
            painter = painterResource(if (connected) R.drawable.circles_connected else R.drawable.circles_disconnected),
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
                ReferenceCircleButton(R.drawable.ic_bell, onNotifications)
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
private fun ReferenceCircleButton(@DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(35.dp).clip(CircleShape).background(Color(0xFF17191F).copy(alpha = .95f))
            .border(1.dp, Color(0xFF20242C), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = QuickPingColors.TextSecondary, modifier = Modifier.size(16.dp))
    }
}
