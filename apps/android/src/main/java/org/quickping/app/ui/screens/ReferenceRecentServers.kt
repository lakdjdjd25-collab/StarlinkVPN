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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun ReferenceRecentServers(
    state: QuickPingUiState,
    onSelectServer: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = quickText("اخیراً متصل‌شده", "Recently connected"),
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF9B9EA7),
            fontFamily = Peyda,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(9.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.servers.take(3).forEach { server ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(21.dp))
                            .background(ReferenceCardColor.copy(alpha = 0.91f))
                            .border(1.dp, ReferenceStrokeColor, RoundedCornerShape(21.dp))
                            .clickable { onSelectServer(server.id) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ReferenceFlag(
                            server = server,
                            modifier = Modifier
                                .size(width = 38.dp, height = 23.dp)
                                .clip(RoundedCornerShape(5.dp)),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = referenceServerTitle(server, state.servers),
                            color = QuickPingColors.TextPrimary,
                            fontFamily = Peyda,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
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
