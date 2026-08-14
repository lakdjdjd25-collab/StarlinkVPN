package org.quickping.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.data.settings.RecentServerStore
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun ReferenceRecentServers(
    state: QuickPingUiState,
    onSelectServer: (String) -> Unit,
) {
    val context = LocalContext.current
    val recentStore = remember(context) { RecentServerStore(context) }
    val validServerIds = state.servers.mapTo(linkedSetOf()) { it.id }
    val serverFingerprint = state.servers.joinToString(separator = ",") { it.id }
    var recentIds by remember(state.user.id, state.service.id) { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(
        state.user.id,
        state.service.id,
        state.connectionStatus,
        state.selectedServerId,
        serverFingerprint,
    ) {
        recentIds = if (
            state.connectionStatus == ConnectionStatus.Connected &&
            state.selectedServerId.isNotBlank()
        ) {
            recentStore.record(
                userId = state.user.id,
                serviceId = state.service.id,
                serverId = state.selectedServerId,
                validServerIds = validServerIds,
            )
        } else {
            recentStore.load(
                userId = state.user.id,
                serviceId = state.service.id,
                validServerIds = validServerIds,
            )
        }
    }

    val serversById = state.servers.associateBy { it.id }
    val recentServers = recentIds.mapNotNull(serversById::get)
    if (recentServers.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().height(149.dp),
    ) {
        Spacer(Modifier.height(15.dp))
        Text(
            text = quickText("اخیراً متصل‌شده", "Recently connected"),
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFA7AAB3),
            fontFamily = Peyda,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(9.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier.fillMaxWidth().height(104.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                recentServers.forEach { server ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(ReferenceCardColor)
                            .border(1.dp, ReferenceStrokeColor, RoundedCornerShape(22.dp))
                            .clickable { onSelectServer(server.id) }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ReferenceFlag(
                            server = server,
                            modifier = Modifier.size(width = 44.dp, height = 27.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = referenceServerTitle(server, state.servers),
                            color = QuickPingColors.TextPrimary,
                            fontFamily = Peyda,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        ReferenceMiniPingChip(server.pingMs)
                    }
                }
            }
        }
    }
}
