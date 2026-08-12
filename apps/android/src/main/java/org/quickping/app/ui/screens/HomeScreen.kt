package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.core.design.MonaSans
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.model.Server
import org.quickping.app.state.QuickPingUiState
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.StatusPill

@Composable
fun HomeScreen(
    state: QuickPingUiState,
    onToggleConnection: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
) {
    val connected = state.connectionStatus == ConnectionStatus.Connected
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 34.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HomeHeader(
                    state = state,
                    onSettings = onSettings,
                    onAccount = onAccount,
                    onNotifications = onNotifications,
                )
            }
            item {
                ConnectionPanel(
                    state = state,
                    onToggleConnection = onToggleConnection,
                )
            }
            item { SuggestedServers(state, onSelectServer) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "همه",
                        color = QuickPingColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "پیشنهادی",
                        color = QuickPingColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(QuickPingColors.Surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_filter),
                            contentDescription = "فیلتر",
                            tint = QuickPingColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(QuickPingColors.Surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = "جستجو",
                            tint = QuickPingColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            items(state.servers, key = { it.id }) { server ->
                ServerRow(
                    server = server,
                    selected = server.id == state.selectedServerId,
                    onClick = { onSelectServer(server.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: QuickPingUiState,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeCircleButton(R.drawable.ic_user, onAccount)
            Spacer(Modifier.width(6.dp))
            StatusPill(
                text = if (state.service.isFree) "رایگان" else state.service.plan,
                color = if (state.service.isFree) QuickPingColors.TextSecondary else QuickPingColors.PrimaryLight,
            )
            Spacer(Modifier.weight(1f))
            HomeCircleButton(R.drawable.ic_bell, onNotifications)
            Spacer(Modifier.width(6.dp))
            HomeCircleButton(R.drawable.ic_settings, onSettings)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "QUICKPING",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = MonaSans,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Text(
            text = when (state.connectionStatus) {
                ConnectionStatus.Connected -> "is Connected"
                ConnectionStatus.Connecting -> "is Connecting"
                ConnectionStatus.Error -> "Connection Error"
                ConnectionStatus.Disconnected -> "is Disconnected"
            },
            color = QuickPingColors.TextMuted,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonaSans),
        )
    }
}

@Composable
private fun HomeCircleButton(@DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(QuickPingColors.Surface.copy(alpha = 0.85f))
            .border(1.dp, QuickPingColors.BorderSoft, CircleShape)
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

@Composable
private fun ConnectionPanel(
    state: QuickPingUiState,
    onToggleConnection: () -> Unit,
) {
    val server = state.servers.firstOrNull { it.id == state.selectedServerId }
        ?: Server(
            id = "unavailable",
            countryCode = "global",
            countryName = "شبکهٔ جهانی",
            title = "سروری در دسترس نیست",
        )
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        if (state.connectionStatus == ConnectionStatus.Connected) {
                            Brush.linearGradient(listOf(Color(0xFF266EF0), Color(0xFF173B7C)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFFBAC4D8), Color(0xFF8993A7)))
                        },
                    )
                    .clickable(enabled = state.servers.isNotEmpty(), onClick = onToggleConnection),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(state.connectionStatus, label = "connectionIcon") { status ->
                    Icon(
                        painter = painterResource(
                            if (status == ConnectionStatus.Connecting) R.drawable.ic_reload else R.drawable.ic_power,
                        ),
                        contentDescription = "اتصال",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state.connectionStatus) {
                        ConnectionStatus.Connected -> "اتصال برقرار است"
                        ConnectionStatus.Connecting -> "در حال اتصال..."
                        ConnectionStatus.Error -> "اتصال ناموفق بود"
                        ConnectionStatus.Disconnected -> "برای اتصال لمس کنید"
                    },
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.service.isFree) "سرویس رایگان" else state.service.name,
                    color = QuickPingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Image(
                    painter = painterResource(flagResource(server.countryCode)),
                    contentDescription = server.countryName,
                    modifier = Modifier.size(width = 48.dp, height = 34.dp),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = server.title,
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SuggestedServers(state: QuickPingUiState, onSelectServer: (String) -> Unit) {
    Column {
        Text(
            "سرورهای پیشنهادی",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            color = QuickPingColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            state.servers.take(3).forEach { server ->
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    onClick = { onSelectServer(server.id) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painterResource(flagResource(server.countryCode)),
                            contentDescription = null,
                            modifier = Modifier.size(width = 30.dp, height = 20.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            server.title,
                            color = QuickPingColors.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(server: Server, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(QuickPingColors.Surface.copy(alpha = 0.92f))
            .border(
                1.dp,
                if (selected) QuickPingColors.Primary else QuickPingColors.BorderSoft,
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(flagResource(server.countryCode)),
            contentDescription = server.countryName,
            modifier = Modifier.size(width = 34.dp, height = 23.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                server.title,
                color = QuickPingColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (server.freeAllowed) "قابل استفاده با سرویس رایگان" else server.countryName,
                color = QuickPingColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = server.pingMs?.let { "$it ms" } ?: "click",
            color = when {
                server.pingMs == null -> QuickPingColors.TextMuted
                server.pingMs < 160 -> QuickPingColors.Success
                server.pingMs < 220 -> QuickPingColors.Warning
                else -> QuickPingColors.Danger
            },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )
    }
}

@DrawableRes
private fun flagResource(countryCode: String): Int = when (countryCode.lowercase()) {
    "de" -> R.drawable.flag_de
    "nl" -> R.drawable.flag_nl
    "us" -> R.drawable.flag_us
    "ir" -> R.drawable.flag_ir
    "global" -> R.drawable.flag_global
    else -> R.drawable.flag_qa
}
