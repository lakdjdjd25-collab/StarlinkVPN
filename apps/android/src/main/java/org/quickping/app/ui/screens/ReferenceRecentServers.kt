package org.quickping.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun ReferenceRecentServers(
    state: QuickPingUiState,
    onSelectServer: (String) -> Unit,
) {
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
                state.servers.filter { it.selectable }.take(3).forEach { server ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(ReferenceCardColor)
                            .border(1.dp, ReferenceStrokeColor, RoundedCornerShape(22.dp))
                            .clickable(enabled = server.selectable) { onSelectServer(server.id) }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ReferenceFlag(
                            server = server,
                            modifier = Modifier.size(width = 44.dp, height = 27.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            if (server.isVip) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_vip_crown),
                                    contentDescription = quickText("سرور VIP", "VIP server"),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        ReferenceMiniPingChip(server.pingMs)
                    }
                }
            }
        }
    }
}
