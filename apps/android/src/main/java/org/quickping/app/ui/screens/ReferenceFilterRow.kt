package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quickping.app.R
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText

@Composable
internal fun ReferenceFilterRow() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReferenceRoundIconButton(
                icon = R.drawable.ic_search,
                description = quickText("جستجو", "Search"),
            )
            Spacer(Modifier.width(5.dp))
            ReferenceRoundIconButton(
                icon = R.drawable.ic_filter,
                description = quickText("فیلتر", "Filter"),
            )
            Spacer(Modifier.weight(1f))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quickText("گیمینگ", "Gaming"),
                        color = QuickPingColors.TextMuted,
                        fontFamily = Peyda,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(15.dp))
                    Text(
                        text = quickText("همه", "All"),
                        color = QuickPingColors.TextPrimary,
                        fontFamily = Peyda,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceRoundIconButton(
    @DrawableRes icon: Int,
    description: String,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFF0C0E12))
            .border(1.dp, Color(0xFF16191F), CircleShape),
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
internal fun ReferenceDashedDivider(modifier: Modifier) {
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
