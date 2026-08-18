package org.quickping.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
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
import org.quickping.app.model.Server

@Composable
internal fun ReferenceBestLocationRow(selected: Boolean, onClick: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(59.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(ReferenceCardColor)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) QuickPingColors.Primary else ReferenceStrokeColor,
                    RoundedCornerShape(28.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = quickText("بهترین مکان", "Best location"),
                modifier = Modifier.weight(1f),
                color = QuickPingColors.TextPrimary,
                fontFamily = Peyda,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ic_rocket),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(29.dp),
            )
        }
    }
}

@Composable
internal fun ReferenceServerRow(
    server: Server,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(59.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(ReferenceCardColor)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) QuickPingColors.Primary else ReferenceStrokeColor,
                    RoundedCornerShape(28.dp),
                )
                .clickable(enabled = server.selectable, onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReferenceFlag(
                server = server,
                modifier = Modifier
                    .size(width = 45.dp, height = 29.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = if (server.selectable) QuickPingColors.TextPrimary else QuickPingColors.TextMuted,
                fontFamily = Peyda,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            if (server.isVip) {
                Spacer(Modifier.width(7.dp))
                ReferenceVipBadge()
            }
            Spacer(Modifier.width(7.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (server.locked) ReferenceLockedChip() else ReferencePingChip(server.pingMs)
            }
        }
    }
}

@Composable
internal fun ReferenceLockedChip() {
    Box(
        modifier = Modifier
            .height(25.dp)
            .width(43.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF20232B))
            .border(1.dp, Color(0xFF343945), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "🔒",
            color = QuickPingColors.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReferenceVipBadge() {
    Box(
        modifier = Modifier
            .width(43.dp)
            .height(25.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6655CE),
                        Color(0xFF9A80D2),
                        Color(0xFFD8D985),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "VIP",
            color = Color.White,
            fontFamily = Peyda,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
