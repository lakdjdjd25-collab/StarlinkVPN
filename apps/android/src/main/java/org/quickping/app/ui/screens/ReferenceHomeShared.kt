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
import org.quickping.app.core.design.quickText
import org.quickping.app.model.Server
import org.quickping.app.model.ServerPingState

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
            lineHeight = (size + 4).sp,
            fontWeight = weight,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ReferencePingChip(server: Server) {
    val pingMs = server.pingMs
    val icon = when {
        server.pingState == ServerPingState.TIMEOUT -> R.drawable.ic_ping_failed
        server.pingState == ServerPingState.UNKNOWN -> R.drawable.ic_ping_failed
        server.pingState == ServerPingState.CONNECTED && pingMs == null -> R.drawable.ic_ping_fast
        pingMs == null -> R.drawable.ic_ping_failed
        pingMs < 160 -> R.drawable.ic_ping_fast
        pingMs < 260 -> R.drawable.ic_ping
        else -> R.drawable.ic_ping_slow
    }
    val tint = when {
        server.pingState == ServerPingState.CONNECTED -> QuickPingColors.Success
        server.pingState in setOf(ServerPingState.TIMEOUT, ServerPingState.UNKNOWN) -> Color.Unspecified
        pingMs == null -> Color.Unspecified
        pingMs < 160 -> QuickPingColors.Success
        pingMs < 260 -> Color(0xFFE2C75C)
        else -> QuickPingColors.Danger
    }
    val label = pingMs?.let { "$it ms" } ?: when (server.pingState) {
        ServerPingState.TIMEOUT -> "Timeout"
        ServerPingState.UNKNOWN -> quickText("نامشخص", "Unknown")
        ServerPingState.CONNECTED -> quickText("متصل", "Connected")
        ServerPingState.CHECKING, ServerPingState.AVAILABLE -> quickText("بررسی", "Check")
    }
    Column(
        modifier = Modifier
            .size(width = 66.dp, height = 43.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(ReferenceChipColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            color = if (pingMs == null) Color(0xFF8B8F99) else QuickPingColors.TextSecondary,
            fontFamily = MonaSans,
            fontSize = 11.sp,
            lineHeight = 12.sp,
        )
    }
}

@Composable
internal fun ReferenceMiniPingChip(server: Server) {
    val pingMs = server.pingMs
    val label = pingMs?.let { "$it ms" } ?: when (server.pingState) {
        ServerPingState.TIMEOUT -> "Timeout"
        ServerPingState.UNKNOWN -> quickText("نامشخص", "Unknown")
        ServerPingState.CONNECTED -> quickText("متصل", "Connected")
        ServerPingState.CHECKING, ServerPingState.AVAILABLE -> quickText("تلاش مجدد", "Retry")
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .height(27.dp)
                .widthIn(min = 58.dp)
                .clip(CircleShape)
                .background(ReferenceChipColor)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color(0xFF7A7F8A),
                fontFamily = MonaSans,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                painter = painterResource(
                    if (server.pingState == ServerPingState.CONNECTED) R.drawable.ic_ping_fast
                    else if (pingMs == null) R.drawable.ic_ping_failed
                    else R.drawable.ic_ping,
                ),
                contentDescription = null,
                tint = when {
                    server.pingState == ServerPingState.CONNECTED -> QuickPingColors.Success
                    pingMs == null -> Color.Unspecified
                    else -> Color(0xFFE2C75C)
                },
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
internal fun ReferenceFlag(server: Server, modifier: Modifier) {
    Image(
        painter = painterResource(referenceFlagResource(server.countryCode)),
        contentDescription = referenceCountryName(server),
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@DrawableRes
@Composable
private fun referenceFlagResource(countryCode: String): Int {
    val context = LocalContext.current
    val rawCode = countryCode.lowercase()
    val code = when (rawCode) {
        "uk" -> "gb"
        else -> rawCode
    }
    val resourceName = when (code) {
        "ir" -> "flag_ir"
        "global" -> "flag_global"
        else -> "flag_${code}_ir"
    }
    val resolved = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    return if (resolved != 0) resolved else R.drawable.flag_global
}

@Composable
internal fun referenceCountryName(server: Server): String = when (server.countryCode.lowercase()) {
    "ir" -> quickText("ایران", "Iran")
    "de" -> quickText("آلمان", "Germany")
    "gb", "uk" -> quickText("بریتانیا", "United Kingdom")
    "fr" -> quickText("فرانسه", "France")
    "it" -> quickText("ایتالیا", "Italy")
    "fi" -> quickText("فنلاند", "Finland")
    "nl" -> quickText("هلند", "Netherlands")
    "us" -> quickText("آمریکا", "United States")
    "ca" -> quickText("کانادا", "Canada")
    "tr" -> quickText("ترکیه", "Turkey")
    "ae" -> quickText("امارات", "United Arab Emirates")
    "ru" -> quickText("روسیه", "Russia")
    "jp" -> quickText("ژاپن", "Japan")
    "sg" -> quickText("سنگاپور", "Singapore")
    "ch" -> quickText("سوئیس", "Switzerland")
    "se" -> quickText("سوئد", "Sweden")
    "no" -> quickText("نروژ", "Norway")
    "es" -> quickText("اسپانیا", "Spain")
    "at" -> quickText("اتریش", "Austria")
    "be" -> quickText("بلژیک", "Belgium")
    "pl" -> quickText("لهستان", "Poland")
    "cz" -> quickText("چک", "Czechia")
    "ro" -> quickText("رومانی", "Romania")
    "bg" -> quickText("بلغارستان", "Bulgaria")
    "gr" -> quickText("یونان", "Greece")
    "pt" -> quickText("پرتغال", "Portugal")
    "dk" -> quickText("دانمارک", "Denmark")
    "ie" -> quickText("ایرلند", "Ireland")
    "in" -> quickText("هند", "India")
    "hk" -> quickText("هنگ‌کنگ", "Hong Kong")
    "au" -> quickText("استرالیا", "Australia")
    "global" -> quickText("جهانی", "Global")
    else -> server.countryName
        .takeIf { it.isNotBlank() && !it.equals(server.countryCode, ignoreCase = true) }
        ?: server.title.takeIf(String::isNotBlank)
        ?: server.countryCode.uppercase()
}

internal fun referenceCountrySearchText(server: Server): String {
    val aliases = when (server.countryCode.lowercase()) {
        "ir" -> "ایران iran"
        "de" -> "آلمان germany deutschland"
        "gb", "uk" -> "بریتانیا انگلیس united kingdom uk england"
        "fr" -> "فرانسه france"
        "it" -> "ایتالیا italy italia"
        "fi" -> "فنلاند finland"
        "nl" -> "هلند netherlands holland"
        "us" -> "آمریکا united states usa"
        "ca" -> "کانادا canada"
        "tr" -> "ترکیه turkey türkiye"
        "ae" -> "امارات united arab emirates uae"
        "ru" -> "روسیه russia"
        "jp" -> "ژاپن japan"
        "sg" -> "سنگاپور singapore"
        "ch" -> "سوئیس switzerland"
        "se" -> "سوئد sweden"
        "no" -> "نروژ norway"
        "es" -> "اسپانیا spain"
        "at" -> "اتریش austria"
        "be" -> "بلژیک belgium"
        "pl" -> "لهستان poland"
        else -> ""
    }
    return listOf(
        server.countryCode,
        server.countryName,
        server.title,
        server.remarks,
        server.host,
        aliases,
    ).joinToString(" ").lowercase()
}

@Composable
internal fun referenceServerTitle(server: Server, allServers: List<Server>): String {
    val sameCountry = allServers.filter { it.countryCode.equals(server.countryCode, ignoreCase = true) }
    val index = sameCountry.indexOfFirst { it.id == server.id }
    val countryName = referenceCountryName(server)
    return if (sameCountry.size > 1 && index > 0) {
        "$countryName ${index + 1}"
    } else {
        countryName
    }
}
