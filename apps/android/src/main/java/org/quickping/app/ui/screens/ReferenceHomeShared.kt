package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quickping.app.R
import org.quickping.app.core.design.MonaSans
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.model.Server

internal val ReferencePanelColor = Color(0xFF080A0D)
internal val ReferenceCardColor = Color(0xFF090B0E)
internal val ReferenceStrokeColor = Color(0xFF171A20)
internal val ReferenceChipColor = Color(0xFF111318)

@Composable
internal fun ReferenceRtlText(
    text: String,
    modifier: Modifier,
    size: Int,
    weight: FontWeight,
    color: Color,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontFamily = Peyda,
            fontSize = size.sp,
            fontWeight = weight,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ReferencePingChip(pingMs: Int?) {
    val icon = when {
        pingMs == null -> R.drawable.ic_ping_failed
        pingMs < 160 -> R.drawable.ic_ping_fast
        pingMs < 260 -> R.drawable.ic_ping
        else -> R.drawable.ic_ping_slow
    }
    val tint = when {
        pingMs == null -> Color.Unspecified
        pingMs < 160 -> QuickPingColors.Success
        pingMs < 260 -> Color(0xFFE2C75C)
        else -> QuickPingColors.Danger
    }
    Column(
        modifier = Modifier
            .size(width = 58.dp, height = 36.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ReferenceChipColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
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
internal fun ReferenceMiniPingChip(pingMs: Int?) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .height(22.dp)
                .widthIn(min = 50.dp)
                .clip(CircleShape)
                .background(ReferenceChipColor)
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
}

@Composable
internal fun ReferenceFlag(server: Server, modifier: Modifier) {
    Image(
        painter = painterResource(referenceFlagResource(server.countryCode)),
        contentDescription = server.countryName,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@DrawableRes
@Composable
private fun referenceFlagResource(countryCode: String): Int {
    val context = LocalContext.current
    val code = countryCode.lowercase()
    val resourceName = when (code) {
        "ir" -> "flag_ir"
        "global" -> "flag_global"
        else -> "flag_${code}_ir"
    }
    val resolved = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    return if (resolved != 0) resolved else R.drawable.flag_global
}

internal fun referenceServerTitle(server: Server, allServers: List<Server>): String {
    val sameCountry = allServers.filter { it.countryCode.equals(server.countryCode, ignoreCase = true) }
    val index = sameCountry.indexOfFirst { it.id == server.id }
    return if (sameCountry.size > 1 && index > 0) {
        "${server.countryName} ${index + 1}"
    } else {
        server.countryName.ifBlank { server.title }
    }
}
