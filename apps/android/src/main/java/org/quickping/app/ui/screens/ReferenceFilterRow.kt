package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quickping.app.R
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText

internal enum class ReferenceQuickFilter { All, Unlimited, Gaming }

@Composable
internal fun ReferenceFilterRow(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    filterActive: Boolean,
    quickFilter: ReferenceQuickFilter,
    onQuickFilterChange: (ReferenceQuickFilter) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(53.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReferenceRoundIconButton(
                icon = R.drawable.ic_search,
                description = quickText("جستجو", "Search"),
                active = searchOpen || query.isNotBlank(),
                onClick = onSearchClick,
            )
            Spacer(Modifier.width(6.dp))
            ReferenceRoundIconButton(
                icon = R.drawable.ic_filter,
                description = quickText("فیلتر", "Filter"),
                active = filterActive,
                onClick = onFilterClick,
            )
            Spacer(Modifier.width(8.dp))
            if (searchOpen) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF0C0E12))
                        .border(1.dp, Color(0xFF20242C), RoundedCornerShape(15.dp))
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (query.isBlank()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = quickText("جستجوی سرور یا کشور", "Search server or country"),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF686D78),
                                fontFamily = Peyda,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = QuickPingColors.TextPrimary,
                                fontFamily = Peyda,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                            ),
                            cursorBrush = SolidColor(QuickPingColors.Primary),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReferenceQuickFilter.entries.forEachIndexed { index, mode ->
                        val selected = quickFilter == mode
                        Text(
                            text = mode.referenceLabel(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onQuickFilterChange(mode) }
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                            color = if (selected) QuickPingColors.TextPrimary else QuickPingColors.TextMuted,
                            fontFamily = Peyda,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        if (index < ReferenceQuickFilter.entries.lastIndex) Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceQuickFilter.referenceLabel(): String = when (this) {
    ReferenceQuickFilter.All -> quickText("همه", "All")
    ReferenceQuickFilter.Unlimited -> quickText("نامحدود ∞", "Unlimited ∞")
    ReferenceQuickFilter.Gaming -> quickText("گیمینگ 🎮", "Gaming 🎮")
}

@Composable
private fun ReferenceRoundIconButton(
    @DrawableRes icon: Int,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF0C0E12))
            .border(
                if (active) 1.5.dp else 1.dp,
                if (active) QuickPingColors.Primary else Color(0xFF1C2028),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (active) Color(0xFFD7DBE6) else QuickPingColors.TextSecondary,
            modifier = Modifier.size(20.dp),
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
