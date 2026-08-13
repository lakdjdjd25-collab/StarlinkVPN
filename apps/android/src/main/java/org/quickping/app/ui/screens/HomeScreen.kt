package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import org.quickping.app.model.Server
import org.quickping.app.state.QuickPingUiState

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            HomeHeader(
                state = state,
                onSettings = onSettings,
                onAccount = onAccount,
                onNotifications = onNotifications,
            )
            ConnectionPanel(
                state = state,
                bestLocationSelected = bestLocationSelected,
                onToggleConnection = onToggleConnection,
            )
            HomeServerList(
                modifier = Modifier.weight(1f),
                state = state,
                bestLocationSelected = bestLocationSelected,
                onSelectBestLocation = {
                    state.servers.firstOrNull()?.let { firstServer ->
                        bestLocationSelected = true
                        onSelectServer(firstServer.id)
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
                .height(54.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, QuickPingColors.Background.copy(alpha = 0.98f)),
                    ),
                ),
        )
    }
}

@Composable
private fun HomeHeader(
    state: QuickPingUiState,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeCircleButton(R.drawable.ic_user, onAccount)
                Spacer(Modifier.width(6.dp))
                HomePlanPill(isFree = state.service.isFree)
                Spacer(Modifier.weight(1f))
                HomeCircleButton(R.drawable.ic_bell, onNotifications)
                Spacer(Modifier.width(6.dp))
                HomeCircleButton(R.drawable.ic_settings, onSettings)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 75.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.home_title_start),
                        contentDescription = null,
                        modifier = Modifier.size(width = 27.dp, height = 13.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "QUICKPING",
                        color = Color.White,
                        fontFamily = MonaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        letterSpacing = (-1.1).sp,
                    )
                    Spacer(Modifier.width(2.dp))
                    Image(
                        painter = painterResource(R.drawable.home_title_end),
                        contentDescription = null,
                        modifier = Modifier.size(width = 27.dp, height = 13.dp),
                    )
                }
            }
            Text(
                text = when (state.connectionStatus) {
                    ConnectionStatus.Connected -> quickText("متصل است", "is Connected")
                    ConnectionStatus.Connecting -> quickText("در حال اتصال", "is Connecting")
                    ConnectionStatus.Error -> quickText("خطای اتصال", "Connection Error")
                    ConnectionStatus.Disconnected -> quickText("قطع است", "is Disconnected")
                },
                color = Color(0xFF8B8D94),
                fontFamily = Bitcount,
                fontWeight = FontWeight.Light,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                letterSpacing = 0.35.sp,
            )
        }
    }
}

@Composable
private fun HomePlanPill(isFree: Boolean) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .width(if (isFree) 54.dp else 64.dp)
            .clip(CircleShape)
            .then(
                if (isFree) {
                    Modifier
                        .background(QuickPingColors.Surface.copy(alpha = 0.82f))
                        .border(1.dp, Color(0xFF3E4654), CircleShape)
                } else {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF6354C8), Color(0xFF9588C8), Color(0xFFD3D884)),
                        ),
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isFree) quickText("رایگان", "Free") else quickText("ارتقا", "Upgrade"),
            color = if (isFree) QuickPingColors.TextSecondary else Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun HomeCircleButton(@DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFF17191F).copy(alpha = 0.94f))
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

@Composable
private fun ConnectionPanel(
    state: QuickPingUiState,
    bestLocationSelected: Boolean,
    onToggleConnection: () -> Unit,
) {
    val server = state.servers.firstOrNull { it.id == state.selectedServerId }
    val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(131.dp)
            .clip(panelShape)
            .background(Color(0xFF080A0D)),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 14.dp, top = 15.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(37.dp))
                        .background(
                            if (state.connectionStatus == ConnectionStatus.Connected) {
                                Brush.linearGradient(listOf(Color(0xFF347CF3), Color(0xFF17478F)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFFA8B0C3), Color(0xFF929AAC)))
                            },
                        )
                        .border(1.dp, Color(0xFF46505C), RoundedCornerShape(37.dp))
                        .clickable(enabled = state.servers.isNotEmpty(), onClick = onToggleConnection),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(state.connectionStatus, label = "connectionIcon") { status ->
                        Icon(
                            painter = painterResource(
                                if (status == ConnectionStatus.Connecting) R.drawable.ic_reload else R.drawable.ic_power,
                            ),
                            contentDescription = quickText("اتصال", "Connect"),
                            tint = Color.White,
                            modifier = Modifier.size(width = 42.dp, height = 45.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                SelectedServerSummary(
                    state = state,
                    server = server,
                    bestLocationSelected = bestLocationSelected,
                )
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
private fun SelectedServerSummary(
    state: QuickPingUiState,
    server: Server?,
    bestLocationSelected: Boolean,
) {
    if (bestLocationSelected && server != null) {
        Column(
            modifier = Modifier.width(110.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 49.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF2A3040), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rocket),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(42.dp),
                )
            }
            Spacer(Modifier.height(5.dp))
            HomeRtlText(
                text = quickText("بهترین مکان", "Best location"),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = QuickPingColors.TextPrimary,
            )
            HomeRtlText(
                text = quickText("شبکهٔ سریع‌تر  •  ------", "Fastest network  •  ------"),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = QuickPingColors.TextMuted,
            )
        }
        return
    }

    if (server == null) {
        Column(
            modifier = Modifier.width(150.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF0900F2)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_globe),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(7.dp))
            HomeRtlText(
                text = quickText("سروری در دسترس نیست", "No server is available"),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = QuickPingColors.TextPrimary,
            )
        }
        return
    }

    Row(verticalAlignment = Alignment.Top) {
        PingChip(server.pingMs)
        Spacer(Modifier.width(7.dp))
        Column(
            modifier = Modifier.width(80.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Image(
                painter = painterResource(flagResource(server.countryCode)),
                contentDescription = server.countryName,
                modifier = Modifier.size(width = 80.dp, height = 49.dp),
            )
            Spacer(Modifier.height(5.dp))
            HomeRtlText(
                text = displayServerTitle(server, state.servers),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = QuickPingColors.TextPrimary,
            )
            HomeRtlText(
                text = if (state.connectionStatus == ConnectionStatus.Error && !state.connectionError.isNullOrBlank()) {
                    state.connectionError
                } else {
                    quickText("شبکهٔ ${server.countryName}  •  ------", "${server.countryName} network  •  ------")
                },
                modifier = Modifier.widthIn(max = 110.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = QuickPingColors.TextMuted,
            )
        }
    }
}

@Composable
private fun HomeRtlText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    color: Color,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontFamily = Peyda,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeServerList(
    modifier: Modifier = Modifier,
    state: QuickPingUiState,
    bestLocationSelected: Boolean,
    onSelectBestLocation: () -> Unit,
    onSelectServer: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp,
            end = 14.dp,
            bottom = 52.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.servers.isNotEmpty()) {
            item(key = "recent") {
                RecentlyConnectedServers(state, onSelectServer)
            }
            item(key = "divider") {
                DashedDivider(
                    modifier = Modifier.padding(
                        start = 29.dp,
                        end = 29.dp,
                        top = 18.dp,
                        bottom = 2.dp,
                    ),
                )
            }
        }
        item(key = "filters") { HomeFilterRow() }
        if (state.servers.isNotEmpty()) {
            item(key = "best-location") {
                BestLocationRow(
                    selected = bestLocationSelected,
                    onClick = onSelectBestLocation,
                )
            }
            items(state.servers, key = { it.id }) { server ->
                ServerRow(
                    server = server,
                    title = displayServerTitle(server, state.servers),
                    selected = !bestLocationSelected && server.id == state.selectedServerId,
                    onClick = { onSelectServer(server.id) },
                )
            }
        }
    }
}

@Composable
private fun RecentlyConnectedServers(
    state: QuickPingUiState,
    onSelectServer: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = quickText("اخیراً متصل‌شده", "Recently connected"),
            modifier = Modifier.fillMaxWidth(),
            color = QuickPingColors.TextSecondary,
            fontFamily = Peyda,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            state.servers.take(3).forEach { server ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF090B0E).copy(alpha = 0.88f))
                        .border(1.dp, Color(0xFF171A20), RoundedCornerShape(20.dp))
                        .clickable { onSelectServer(server.id) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painterResource(flagResource(server.countryCode)),
                        contentDescription = null,
                        modifier = Modifier.size(width = 34.dp, height = 21.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = displayServerTitle(server, state.servers),
                        color = QuickPingColors.TextPrimary,
                        fontFamily = Peyda,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    MiniPingChip(server.pingMs)
                }
            }
        }
    }
}

@Composable
private fun HomeFilterRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = quickText("همه", "All"),
            color = QuickPingColors.TextPrimary,
            fontFamily = Peyda,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = quickText("کمپینگ", "Campaign"),
            color = QuickPingColors.TextMuted,
            fontFamily = Peyda,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        HomeRoundIconButton(R.drawable.ic_filter, quickText("فیلتر", "Filter"))
        Spacer(Modifier.width(4.dp))
        HomeRoundIconButton(R.drawable.ic_search, quickText("جستجو", "Search"))
    }
}

@Composable
private fun HomeRoundIconButton(@DrawableRes icon: Int, description: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF0C0E12)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = QuickPingColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp),
    ) {
        drawLine(
            color = Color(0xFF1C1F25),
            start = androidx.compose.ui.geometry.Offset(0f, center.y),
            end = androidx.compose.ui.geometry.Offset(size.width, center.y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)),
        )
    }
}

@Composable
private fun BestLocationRow(selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF090B0E).copy(alpha = 0.9f))
            .border(
                1.dp,
                if (selected) QuickPingColors.Primary else Color(0xFF171A20),
                RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_rocket),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = quickText("بهترین مکان", "Best location"),
            color = QuickPingColors.TextPrimary,
            fontFamily = Peyda,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ServerRow(server: Server, title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF090B0E).copy(alpha = 0.9f))
            .border(
                1.dp,
                if (selected) QuickPingColors.Primary else Color(0xFF171A20),
                RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(flagResource(server.countryCode)),
            contentDescription = server.countryName,
            modifier = Modifier.size(width = 34.dp, height = 22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = QuickPingColors.TextPrimary,
            fontFamily = Peyda,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PingChip(server.pingMs)
    }
}

@Composable
private fun PingChip(pingMs: Int?) {
    val icon = when {
        pingMs == null -> R.drawable.ic_ping_failed
        pingMs < 160 -> R.drawable.ic_ping_fast
        pingMs < 260 -> R.drawable.ic_ping
        else -> R.drawable.ic_ping_slow
    }
    val iconTint = when {
        pingMs == null -> Color.Unspecified
        pingMs < 160 -> QuickPingColors.Success
        pingMs < 260 -> Color(0xFFE2C75C)
        else -> QuickPingColors.Danger
    }
    Column(
        modifier = Modifier
            .size(width = 54.dp, height = 34.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF111318)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(width = 11.dp, height = 12.dp),
        )
        Text(
            text = pingMs?.let { "$it ms" } ?: "click",
            color = if (pingMs == null) Color(0xFF8B8F99) else QuickPingColors.TextSecondary,
            fontFamily = MonaSans,
            fontSize = 10.sp,
            lineHeight = 11.sp,
        )
    }
}

@Composable
private fun MiniPingChip(pingMs: Int?) {
    Row(
        modifier = Modifier
            .height(21.dp)
            .widthIn(min = 48.dp)
            .clip(CircleShape)
            .background(Color(0xFF111318))
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pingMs?.let { "$it ms" } ?: "retry",
            color = Color(0xFF686D78),
            fontFamily = MonaSans,
            fontSize = 9.sp,
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            painter = painterResource(if (pingMs == null) R.drawable.ic_ping_failed else R.drawable.ic_ping),
            contentDescription = null,
            tint = if (pingMs == null) Color.Unspecified else Color(0xFFE2C75C),
            modifier = Modifier.size(11.dp),
        )
    }
}

private fun displayServerTitle(server: Server, allServers: List<Server>): String {
    val sameCountry = allServers.filter { it.countryCode.equals(server.countryCode, ignoreCase = true) }
    val index = sameCountry.indexOfFirst { it.id == server.id }
    return if (sameCountry.size > 1 && index > 0) {
        "${server.countryName} ${index + 1}"
    } else {
        server.countryName.ifBlank { server.title }
    }
}

@DrawableRes
private fun flagResource(countryCode: String): Int = when (countryCode.lowercase()) {
    "ad" -> R.drawable.flag_ad_ir
    "ae" -> R.drawable.flag_ae_ir
    "al" -> R.drawable.flag_al_ir
    "am" -> R.drawable.flag_am_ir
    "ar" -> R.drawable.flag_ar_ir
    "at" -> R.drawable.flag_at_ir
    "az" -> R.drawable.flag_az_ir
    "ba" -> R.drawable.flag_ba_ir
    "be" -> R.drawable.flag_be_ir
    "bg" -> R.drawable.flag_bg_ir
    "bh" -> R.drawable.flag_bh_ir
    "br" -> R.drawable.flag_br_ir
    "by" -> R.drawable.flag_by_ir
    "ca" -> R.drawable.flag_ca_ir
    "ch" -> R.drawable.flag_ch_ir
    "cl" -> R.drawable.flag_cl_ir
    "cn" -> R.drawable.flag_cn_ir
    "co" -> R.drawable.flag_co_ir
    "cr" -> R.drawable.flag_cr_ir
    "cy" -> R.drawable.flag_cy_ir
    "cz" -> R.drawable.flag_cz_ir
    "de" -> R.drawable.flag_de_ir
    "dk" -> R.drawable.flag_dk_ir
    "ec" -> R.drawable.flag_ec_ir
    "ee" -> R.drawable.flag_ee_ir
    "es" -> R.drawable.flag_es_ir
    "fi" -> R.drawable.flag_fi_ir
    "fr" -> R.drawable.flag_fr_ir
    "gb" -> R.drawable.flag_gb_ir
    "ge" -> R.drawable.flag_ge_ir
    "gr" -> R.drawable.flag_gr_ir
    "gt" -> R.drawable.flag_gt_ir
    "hr" -> R.drawable.flag_hr_ir
    "hu" -> R.drawable.flag_hu_ir
    "ie" -> R.drawable.flag_ie_ir
    "im" -> R.drawable.flag_im_ir
    "in" -> R.drawable.flag_in_ir
    "nl" -> R.drawable.flag_nl_ir
    "no" -> R.drawable.flag_no_ir
    "om" -> R.drawable.flag_om_ir
    "pa" -> R.drawable.flag_pa_ir
    "pe" -> R.drawable.flag_pe_ir
    "pk" -> R.drawable.flag_pk_ir
    "pl" -> R.drawable.flag_pl_ir
    "pt" -> R.drawable.flag_pt_ir
    "qa" -> R.drawable.flag_qa_ir
    "ro" -> R.drawable.flag_ro_ir
    "rs" -> R.drawable.flag_rs_ir
    "ru" -> R.drawable.flag_ru_ir
    "sa" -> R.drawable.flag_sa_ir
    "se" -> R.drawable.flag_se_ir
    "si" -> R.drawable.flag_si_ir
    "sk" -> R.drawable.flag_sk_ir
    "tr" -> R.drawable.flag_tr_ir
    "ua" -> R.drawable.flag_ua_ir
    "us" -> R.drawable.flag_us_ir
    "uy" -> R.drawable.flag_uy_ir
    "ve" -> R.drawable.flag_ve_ir
    "ir" -> R.drawable.flag_ir
    "global" -> R.drawable.flag_global
    else -> R.drawable.flag_qa_ir
}
