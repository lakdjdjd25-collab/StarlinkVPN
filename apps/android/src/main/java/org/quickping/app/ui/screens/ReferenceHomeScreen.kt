package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.quickping.app.core.design.Bitcount
import org.quickping.app.core.design.MonaSans
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun ReferenceHomeScreen(
    state: QuickPingUiState,
    onToggleConnection: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
) {
    val connected = state.connectionStatus == ConnectionStatus.Connected
    var bestLocationSelected by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuickPingColors.Background),
    ) {
        Image(
            painter = painterResource(
                if (connected) R.drawable.bg_home_connected else R.drawable.bg_home_disconnected,
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(
                if (connected) R.drawable.circles_connected else R.drawable.circles_disconnected,
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(286.dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ReferenceHomeHeader(
                state = state,
                onSettings = onSettings,
                onAccount = onAccount,
                onNotifications = onNotifications,
            )
            ReferenceConnectionPanel(
                state = state,
                bestLocationSelected = bestLocationSelected,
                onToggleConnection = onToggleConnection,
            )
            ReferenceServerList(
                modifier = Modifier.weight(1f),
                state = state,
                bestLocationSelected = bestLocationSelected,
                onSelectBestLocation = {
                    state.servers.firstOrNull()?.let { server ->
                        bestLocationSelected = true
                        onSelectServer(server.id)
                    }
                },
                onSelectServer = { serverId ->
                    bestLocationSelected = false
                    onSelectServer(serverId)
                },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, QuickPingColors.Background.copy(alpha = 0.99f)),
                    ),
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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReferenceCircleButton(R.drawable.ic_user, onAccount)
                Spacer(Modifier.width(7.dp))
                ReferencePlanPill(state.service.isFree)
                Spacer(Modifier.weight(1f))
                ReferenceCircleButton(R.drawable.ic_bell, onNotifications)
                Spacer(Modifier.width(7.dp))
                ReferenceCircleButton(R.drawable.ic_settings, onSettings)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.home_title_start),
                        contentDescription = null,
                        modifier = Modifier.size(width = 31.dp, height = 14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "QUICKPING",
                        color = Color.White,
                        fontFamily = MonaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 31.sp,
                        lineHeight = 33.sp,
                        letterSpacing = (-1.05).sp,
                    )
                    Spacer(Modifier.width(3.dp))
                    Image(
                        painter = painterResource(R.drawable.home_title_end),
                        contentDescription = null,
                        modifier = Modifier.size(width = 31.dp, height = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = when (state.connectionStatus) {
                    ConnectionStatus.Connected -> "is Connected"
                    ConnectionStatus.Connecting -> "is Connecting"
                    ConnectionStatus.Error -> "Connection Error"
                    ConnectionStatus.Disconnected -> "is Disconnected"
                },
                color = Color(0xFF8B8D94),
                fontFamily = Bitcount,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.32.sp,
            )
        }
    }
}

@Composable
private fun ReferencePlanPill(isFree: Boolean) {
    Box(
        modifier = Modifier
            .height(35.dp)
            .width(if (isFree) 56.dp else 67.dp)
            .clip(CircleShape)
            .then(
                if (isFree) {
                    Modifier
                        .background(QuickPingColors.Surface.copy(alpha = 0.82f))
                        .border(1.dp, Color(0xFF3E4654), CircleShape)
                } else {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF6555CE), Color(0xFFA28DCE), Color(0xFFD3D884)),
                        ),
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isFree) quickText("رایگان", "Free") else quickText("ارتقا", "Upgrade"),
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
        modifier = Modifier
            .size(35.dp)
            .clip(CircleShape)
            .background(Color(0xFF17191F).copy(alpha = 0.95f))
            .border(1.dp, Color(0xFF20242C), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = QuickPingColors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}
