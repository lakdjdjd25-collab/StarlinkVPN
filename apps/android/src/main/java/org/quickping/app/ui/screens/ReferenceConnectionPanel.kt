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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.InetAddress
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.quickping.app.R
import org.quickping.app.core.design.Peyda
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
    val canToggle = connected || state.connectionStatus == ConnectionStatus.Connecting || server?.selectable == true
    val panelShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(151.dp)
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
                        .size(114.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(if (connected) Color(0xFFE1E7F7) else Color(0xFF8B90A3))
                        .border(
                            1.dp,
                            if (connected) Color(0xFFF0F3FC) else Color(0xFF9BA1B5),
                            RoundedCornerShape(40.dp),
                        )
                        .clickable(enabled = canToggle, onClick = onToggleConnection),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(state.connectionStatus, label = "referenceConnectionIcon") { status ->
                        Icon(
                            painter = painterResource(
                                if (status == ConnectionStatus.Connecting) R.drawable.ic_reload else R.drawable.ic_power,
                            ),
                            contentDescription = quickText("اتصال", "Connect"),
                            tint = if (status == ConnectionStatus.Connected) Color(0xFF242A36) else Color.White,
                            modifier = Modifier.size(width = 49.dp, height = 52.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                ReferenceSelectedSummary(state, server)
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
) {
    if (server == null) {
        ReferenceRtlText(
            quickText("سروری در دسترس نیست", "No server is available"),
            Modifier.width(165.dp),
            14,
            FontWeight.SemiBold,
            QuickPingColors.TextPrimary,
        )
        return
    }

    val connected = state.connectionStatus == ConnectionStatus.Connected
    var connectedAt by rememberSaveable(server.id) { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.connectionStatus, server.id) {
        if (connected) {
            if (connectedAt <= 0L) connectedAt = System.currentTimeMillis()
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000)
            }
        } else {
            connectedAt = 0L
            now = System.currentTimeMillis()
        }
    }

    val selectedAddress by produceState(
        initialValue = server.host.ifBlank { "—" },
        key1 = server.id,
        key2 = server.host,
    ) {
        value = withContext(Dispatchers.IO) {
            if (server.host.isBlank()) return@withContext "—"
            runCatching { InetAddress.getByName(server.host).hostAddress }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: server.host
        }
    }

    Row(verticalAlignment = Alignment.Top) {
        ReferencePingChip(server)
        Spacer(Modifier.width(11.dp))
        Column(
            modifier = Modifier.width(132.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReferenceFlag(
                server,
                Modifier
                    .size(width = 92.dp, height = 56.dp)
                    .clip(RoundedCornerShape(13.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = referenceCountryName(server),
                modifier = Modifier.width(110.dp),
                color = QuickPingColors.TextPrimary,
                fontFamily = Peyda,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            val info = when {
                server.locked -> quickText(
                    "نیازمند دسترسی VIP",
                    "VIP access required",
                    nl = "VIP-toegang vereist",
                    ar = "يتطلب وصول VIP",
                    tr = "VIP erişimi gerekli",
                    ru = "Требуется VIP-доступ",
                    hi = "VIP एक्सेस आवश्यक",
                    zh = "需要 VIP 权限",
                    ur = "VIP رسائی درکار ہے",
                )
                state.connectionStatus == ConnectionStatus.Error && !state.connectionError.isNullOrBlank() -> {
                    state.connectionErrorCode
                        ?.takeIf(String::isNotBlank)
                        ?.let { code -> "[$code] ${state.connectionError.orEmpty()}" }
                        ?: state.connectionError.orEmpty()
                }
                connected -> {
                    val elapsed = (now - connectedAt).coerceAtLeast(0L)
                    "${formatConnectionDuration(elapsed)}  •  $selectedAddress"
                }
                state.connectionStatus == ConnectionStatus.Connecting -> quickText(
                    "در حال اتصال…  •  $selectedAddress",
                    "Connecting…  •  $selectedAddress",
                )
                else -> "--:--:--  •  $selectedAddress"
            }
            Text(
                text = info,
                modifier = Modifier.widthIn(max = 132.dp),
                color = if (connected) Color(0xFF8F949F) else QuickPingColors.TextMuted,
                fontFamily = Peyda,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun formatConnectionDuration(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
