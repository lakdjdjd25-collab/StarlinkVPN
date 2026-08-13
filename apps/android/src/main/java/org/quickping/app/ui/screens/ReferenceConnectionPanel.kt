package org.quickping.app.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.model.Server
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun ReferenceConnectionPanel(
    state: QuickPingUiState,
    bestLocationSelected: Boolean,
    onToggleConnection: () -> Unit,
) {
    val server = state.servers.firstOrNull { it.id == state.selectedServerId }
    val connected = state.connectionStatus == ConnectionStatus.Connected
    val panelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .clip(panelShape)
            .background(ReferencePanelColor),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 15.dp, top = 16.dp, bottom = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(39.dp))
                        .background(if (connected) Color(0xFFDDE5FA) else Color(0xFF8B90A3))
                        .border(
                            1.dp,
                            if (connected) Color(0xFFE9EEFC) else Color(0xFF9BA1B5),
                            RoundedCornerShape(39.dp),
                        )
                        .clickable(enabled = state.servers.isNotEmpty(), onClick = onToggleConnection),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(state.connectionStatus, label = "referenceConnectionIcon") { status ->
                        Icon(
                            painter = painterResource(
                                if (status == ConnectionStatus.Connecting) R.drawable.ic_reload else R.drawable.ic_power,
                            ),
                            contentDescription = quickText("اتصال", "Connect"),
                            tint = if (status == ConnectionStatus.Connected) Color(0xFF242A36) else Color.White,
                            modifier = Modifier.size(width = 45.dp, height = 48.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                ReferenceSelectedSummary(state, server, bestLocationSelected)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1A1D22)),
        )
    }
}

@Composable
private fun ReferenceSelectedSummary(
    state: QuickPingUiState,
    server: Server?,
    bestLocationSelected: Boolean,
) {
    if (bestLocationSelected && server != null) {
        Column(Modifier.width(125.dp), horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 53.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF2A3040), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rocket),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(5.dp))
            ReferenceRtlText(
                quickText("بهترین مکان", "Best location"),
                Modifier.fillMaxWidth(),
                19,
                FontWeight.Bold,
                QuickPingColors.TextPrimary,
            )
            ReferenceRtlText(
                quickText("شبکهٔ سریع‌تر  •  ------", "Fastest network  •  ------"),
                Modifier.fillMaxWidth(),
                10,
                FontWeight.Normal,
                QuickPingColors.TextMuted,
            )
        }
        return
    }

    if (server == null) {
        ReferenceRtlText(
            quickText("سروری در دسترس نیست", "No server is available"),
            Modifier.width(155.dp),
            13,
            FontWeight.SemiBold,
            QuickPingColors.TextPrimary,
        )
        return
    }

    Row(verticalAlignment = Alignment.Top) {
        ReferencePingChip(server.pingMs)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(90.dp), horizontalAlignment = Alignment.End) {
            ReferenceFlag(
                server,
                Modifier.size(width = 86.dp, height = 53.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(6.dp))
            ReferenceRtlText(
                referenceServerTitle(server, state.servers),
                Modifier.fillMaxWidth(),
                19,
                FontWeight.Bold,
                QuickPingColors.TextPrimary,
            )
            val info = if (state.connectionStatus == ConnectionStatus.Error && !state.connectionError.isNullOrBlank()) {
                state.connectionErrorCode
                    ?.takeIf(String::isNotBlank)
                    ?.let { code -> "[$code] ${state.connectionError.orEmpty()}" }
                    ?: state.connectionError.orEmpty()
            } else {
                quickText(
                    "شبکهٔ ${server.countryName}  •  ------",
                    "${server.countryName} network  •  ------",
                )
            }
            ReferenceRtlText(
                info,
                Modifier.widthIn(max = 125.dp),
                10,
                FontWeight.Normal,
                QuickPingColors.TextMuted,
            )
        }
    }
}
